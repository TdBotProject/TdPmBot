package io.github.nekohasekai.pm.instance

import cn.hutool.core.io.FileUtil
import io.github.nekohasekai.nekolib.core.client.TdBot
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.LICENSE
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.*
import io.github.nekohasekai.pm.manage.SetIntegration
import io.github.nekohasekai.pm.manage.SetStartMessages
import org.jetbrains.exposed.sql.deleteWhere
import td.TdApi

class PmBot(botToken: String, val userBot: UserBot) : TdBot(botToken), PmInstance {

    override val database = Launcher.database

    override val admin get() = userBot.owner.toLong()
    override val L get() = LocaleController.forChat(userBot.owner)

    override val integration get() = BotIntegration.Cache.fetch(botUserId).value
    override val blocks = UserBlocks.Cache(botUserId)

    override suspend fun onAuthorizationState(authorizationState: TdApi.AuthorizationState) {

        if (auth && authorizationState is TdApi.AuthorizationStateClosed) {

            defaultLog.info("${me.displayName} (@${me.username}): PmBot Closed")

        }

        super.onAuthorizationState(authorizationState)

    }

    override suspend fun onLogin() {

        defaultLog.info("${me.displayName} (@${me.username}): PmBot Loaded")

        upsertCommands()

    }

    suspend fun destroy() {

        waitForClose()

        FileUtil.del(options.databaseDirectory)

        database.write {

            MessageRecords.deleteWhere { currentBot }
            UserBot.removeFromCache(userBot)
            UserBots.deleteWhere { UserBots.botId eq botUserId }

        }

        BotInstances.instanceMap.remove(botUserId)

    }

    override suspend fun onAuthorizationFailed(ex: TdException) {

        destroy()

        Launcher make L.BOT_LOGOUT.input(userBot.username) sendTo userBot.owner

    }

    override suspend fun onLogout() {

        destroy()

        Launcher make L.BOT_LOGOUT.input(userBot.username) sendTo userBot.owner

    }

    override fun onLoad() {

        options databaseDirectory "data/pm/$botUserId"

        addHandler(InputHandler(this))
        addHandler(OutputHandler(this))
        addHandler(EditHandler(this))
        addHandler(DeleteHandler(this))
        addHandler(JoinHandler(this))
        addHandler(BlockHandler(this))
        addHandler(SetStartMessages())
        addHandler(SetIntegration())

        initStartPayload("finish_creation")

    }

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        if (blocks.containsKey(userId)) finishEvent()

        super.onNewMessage(userId, chatId, message)

    }

    override suspend fun onUndefinedFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (chatId != admin && message.fromPrivate) rejectFunction() else super.onUndefinedFunction(userId, chatId, message, function, param, params, originParams)

    }

    override suspend fun onUndefinedPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        if (chatId != admin && message.fromPrivate) rejectFunction() else super.onUndefinedPayload(userId, chatId, message, payload, params)

    }

    override suspend fun onLaunch(userId: Int, chatId: Long, message: TdApi.Message) {

        if (!message.fromPrivate) return

        if (chatId != admin) {

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

        } else {

            sudo make L.PM_HELP sendTo chatId

        }

    }

    override suspend fun onStartPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        if (payload == "finish_creation") {

            if (chatId != admin) rejectFunction()

            sudo make L.CREATE_FINISHED sendTo chatId

        }

    }

}