package io.nekohasekai.pm.instance

import cn.hutool.core.io.FileUtil
import io.nekohasekai.ktlib.core.defaultLog
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.core.TdBot
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.extensions.displayName
import io.nekohasekai.ktlib.td.core.extensions.fromPrivate
import io.nekohasekai.ktlib.td.core.raw.getChat
import io.nekohasekai.ktlib.td.core.raw.getChatOrNull
import io.nekohasekai.ktlib.td.core.utils.*
import io.nekohasekai.ktlib.td.i18n.CANCELED
import io.nekohasekai.ktlib.td.i18n.LICENSE
import io.nekohasekai.ktlib.td.utils.toLink
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.*
import io.nekohasekai.pm.manage.menu.*
import org.jetbrains.exposed.sql.*
import td.TdApi

class PmBot(botToken: String, val userBot: UserBot) : TdBot(botToken), PmInstance {

    override val database = Launcher.database

    override val admin get() = userBot.owner.toLong()

    override val integration get() = BotIntegration.Cache.fetch(botUserId).value
    override val settings get() = BotSetting.Cache.fetch(botUserId).value

    override val blocks by lazy { UserBlocks.Cache(botUserId) }

    override suspend fun onAuthorizationState(authorizationState: TdApi.AuthorizationState) {

        if (auth && authorizationState is TdApi.AuthorizationStateClosed) {

            defaultLog.info("${me.displayName} (@${me.username}): PmBot Closed")

        }

        super.onAuthorizationState(authorizationState)

    }

    suspend fun updateCommands() {

        upsertCommands(* database {
            BotCommands
                    .select { commandsForCurrentBot and (BotCommands.hide eq false) }
                    .map { TdApi.BotCommand(it[BotCommands.command], it[BotCommands.description]) }
                    .toTypedArray()
        })

    }

    override suspend fun onLogin() {

        defaultLog.info("${me.displayName} (@${me.username}): PmBot Loaded")

        updateCommands()

        runCatching {

            getChat(admin)

            val integration = integration?.integration

            if (integration != null) getChat(integration)

        }

    }

    override suspend fun gc() {

        super.gc()

        gc(this)

    }

    suspend fun destroy() {

        waitForClose()

        FileUtil.del(options.databaseDirectory)

        database.write {

            UserBlocks.deleteWhere { UserBlocks.botId eq botUserId }
            StartMessages.deleteWhere { StartMessages.botId eq botUserId }
            MessageRecords.deleteWhere { messagesForCurrentBot }
            UserBot.removeFromCache(userBot)
            UserBots.deleteWhere { UserBots.botId eq botUserId }

        }

        BotInstances.instanceMap.remove(botUserId)

    }

    override suspend fun onAuthorizationFailed(ex: TdException) {

        destroy()

        val owner = admin

        Launcher.apply {

            getChatOrNull(owner) ?: return

            sudo make L.BOT_AUTH_FAILED.input(userBot.username) sendTo owner

        }

    }

    override suspend fun onLogout() {

        destroy()

        val owner = admin

        Launcher.apply {

            getChatOrNull(owner) ?: return

            sudo make L.BOT_LOGOUT.input(userBot.username) sendTo owner

        }

    }

    override fun onLoad() {

        options databaseDirectory "data/pm/$botUserId"

        addHandler(InputHandler(this))
        addHandler(OutputHandler(this))
        addHandler(EditHandler(this))
        addHandler(DeleteHandler(this))
        addHandler(JoinHandler(this))
        addHandler(BlockHandler(this))
        addHandler(RecallHandler(this))

        addHandler(StartMessagesMenu())
        addHandler(IntegrationMenu())
        addHandler(CommandsMenu())

        initStartPayload("finish_creation")

    }

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        if (blocks.fetch(userId).value == true) finishEvent()

        super.onNewMessage(userId, chatId, message)

    }

    override suspend fun onUndefinedFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (function == "cancel") {

            if (chatId == admin) {

                super.onUndefinedFunction(userId, chatId, message, function, param, params, originParams)

            } else {

                rejectFunction()

            }

        }

        val command = BotCommands.Cache.fetch(me.id to function).value?.takeIf { !it.hide }

        if (!message.fromPrivate) {

            if (command == null) return

            command.messages.forEach { sudo make it syncTo chatId }

            return

        } else {

            if (command == null) rejectFunction()

            command.messages.forEach { sudo make it syncTo chatId }

            if (chatId != admin) writePersist(userId, PERSIST_UNDER_FUNCTION, 0L, function)

        }

    }

    override suspend fun onUndefinedPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        if (message.fromPrivate) {

            val command = BotCommands.Cache.fetch(me.id to payload).value?.takeIf { !it.hide } ?: rejectFunction()

            command.messages.forEach { sudo make it syncTo chatId }

            if (chatId != admin) writePersist(userId, PERSIST_UNDER_FUNCTION, 0L, payload)

        } else rejectFunction()

    }

    override suspend fun onLaunch(userId: Int, chatId: Long, message: TdApi.Message) {

        if (!message.fromPrivate) return

        val startMessages = StartMessages.Cache.fetch(botUserId).value

        if (startMessages == null) {

            var content = L.DEFAULT_WELCOME

            if (Launcher.public) {

                content += "\n\n" + L.POWERED_BY.input(Launcher.me.username, L.LICENSE.input(Launcher.repoName, Launcher.licenseUrl, "Github Repo".toLink(Launcher.repoUrl)))

            }

            sudo makeHtml content sendTo chatId

        } else {

            startMessages.forEach {

                sudo make it syncTo chatId

            }

        }

        if (chatId == admin) {

            sudo make L.PM_HELP sendTo chatId

        }

    }

    override suspend fun onStartPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        if (payload == "finish_creation") {

            if (chatId != admin) rejectFunction()

            sudo make L.CREATE_FINISHED sendTo chatId

        }

    }

    override suspend fun onSendCanceledMessage(userId: Int, chatId: Long) {

        sudo make L.CANCELED withMarkup removeKeyboard() syncTo userId

    }

}