package io.nekohasekai.pm.instance

import cn.hutool.core.io.FileUtil
import io.nekohasekai.ktlib.core.defaultLog
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.cli.TdBot
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.raw.getChat
import io.nekohasekai.ktlib.td.core.raw.getChatOrNull
import io.nekohasekai.ktlib.td.extensions.displayNameFormatted
import io.nekohasekai.ktlib.td.extensions.fromPrivate
import io.nekohasekai.ktlib.td.extensions.htmlLink
import io.nekohasekai.ktlib.td.i18n.CANCELED
import io.nekohasekai.ktlib.td.i18n.LICENSE
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.ktlib.td.utils.removeKeyboard
import io.nekohasekai.ktlib.td.utils.upsertCommands
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.*
import io.nekohasekai.pm.manage.menu.BotMenu
import io.nekohasekai.pm.manage.menu.CommandsMenu
import io.nekohasekai.pm.manage.menu.IntegrationMenu
import io.nekohasekai.pm.manage.menu.StartMessagesMenu
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import td.TdApi

class PmBot(botToken: String, val userBot: UserBot, val launcher: TdPmBot) : TdBot(botToken), PmInstance {

    override val database = launcher.database

    override val admin get() = userBot.owner.toLong()

    override val integration get() = launcher.botIntegrations.fetch(botUserId).value
    override val settings get() = launcher.botSettings.fetch(botUserId).value

    override val blocks by lazy { UserBlocks.Cache(database, botUserId) }

    override suspend fun onAuthorizationState(authorizationState: TdApi.AuthorizationState) {

        if (auth && authorizationState is TdApi.AuthorizationStateClosed) {

            defaultLog.debug("[${me.displayNameFormatted}] PmBot Closed")

        }

        super.onAuthorizationState(authorizationState)

    }

    suspend fun updateCommands() {

        upsertCommands(* database {
            BotCommands
                .select { commandsForCurrentBot and (BotCommands.hide eq false) and (BotCommands.disable eq false) }
                .map { TdApi.BotCommand(it[BotCommands.command], it[BotCommands.description]) }
                .toTypedArray()
        })

    }

    override suspend fun onLogin() {

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

            UserBot.removeFromCache(userBot)
            UserBots.deleteWhere { UserBots.botId eq botUserId }

        }

        launcher.instanceMap.remove(botUserId)

    }

    override suspend fun onAuthorizationFailed(ex: TdException) {

        destroy()

        val owner = admin
        val userBot = userBot

        launcher.apply {

            getChatOrNull(owner) ?: return

            sudo make L.BOT_AUTH_FAILED.input(userBot.username) sendTo owner

        }

    }

    override suspend fun onLogout() {

        destroy()

        val owner = admin
        val userBot = userBot

        launcher.apply {

            getChatOrNull(owner) ?: return

            sudo make L.BOT_LOGOUT.input(userBot.username) sendTo owner

        }

    }

    override fun onLoad() {

        options databaseDirectory "${launcher.dataDir}/pm/$botUserId"

        addHandler(InputHandler(this))
        addHandler(OutputHandler(this))
        addHandler(EditHandler(this))
        addHandler(DeleteHandler(this))
        addHandler(JoinHandler(this))
        addHandler(BlockHandler(this))
        addHandler(RecallHandler(this))
        addHandler(UpgradeHandler(this))

        addHandler(StartMessagesMenu())
        addHandler(IntegrationMenu())
        addHandler(CommandsMenu())

        initStartPayload("finish_creation")

    }

    override suspend fun userBlocked(userId: Int) = blocks.fetch(userId).value == true

    override suspend fun onUndefinedFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>,
        originParams: Array<String>
    ) {

        if (function == "cancel") {

            if (chatId == admin) {

                super.onUndefinedFunction(userId, chatId, message, function, param, params, originParams)

            } else {

                rejectFunction()

            }

        }

        val command = launcher.botCommands.fetch(me.id to function).value?.takeIf { !it.disable }

        if (!message.fromPrivate) {

            if (command == null) return

            command.messages.forEach { sudo make it syncTo chatId }

            return

        } else {

            if (command == null) rejectFunction()

            command.messages.forEach { sudo make it syncTo chatId }

            if (chatId != admin) writePersist(userId, PERSIST_UNDER_FUNCTION, 0, function)

        }

    }

    override suspend fun onUndefinedPayload(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        payload: String,
        params: Array<String>
    ) {

        if (message.fromPrivate) {

            val command = launcher.botCommands.fetch(me.id to payload).value?.takeIf { !it.disable } ?: rejectFunction()

            command.messages.forEach { sudo make it syncTo chatId }

            if (chatId != admin) writePersist(userId, PERSIST_UNDER_FUNCTION, 0, payload)

        } else rejectFunction()

    }

    override suspend fun onLaunch(userId: Int, chatId: Long, message: TdApi.Message) {

        if (!message.fromPrivate) return

        val startMessages = launcher.startMessages.fetch(botUserId).value

        if (startMessages == null) {

            var content = L.DEFAULT_WELCOME

            if (launcher.public) {

                content += "\n\n" + L.POWERED_BY.input(
                    launcher.me.username,
                    L.LICENSE.input(TdPmBot.repoName, TdPmBot.licenseUrl, "Github Repo".htmlLink(TdPmBot.repoUrl))
                )

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

    override suspend fun onStartPayload(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        payload: String,
        params: Array<String>
    ) {

        if (payload == "finish_creation") {

            if (chatId != admin) rejectFunction()

            launcher.findHandler<BotMenu>().botMenu(userId, chatId, 0L, false, me.id, userBot)

            sudo make L.CREATE_FINISHED sendTo chatId

        }

    }

    override suspend fun onSendCanceledMessage(userId: Int, chatId: Long) {

        sudo make L.CANCELED withMarkup removeKeyboard() syncTo userId

    }

}