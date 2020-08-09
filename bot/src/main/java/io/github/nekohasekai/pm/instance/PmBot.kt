package io.github.nekohasekai.pm.instance

import cn.hutool.core.io.FileUtil
import io.github.nekohasekai.nekolib.core.client.TdBot
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.LICENSE
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.*
import io.github.nekohasekai.pm.manage.SetStartMessages
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import td.TdApi

class PmBot(botToken: String, val userBot: UserBot) : TdBot(botToken), PmInstance {

    val owner = userBot.owner

    override val database = Launcher.database
    override val messageRecords = MessageRecords(botUserId)
    override val messages = MessageRecordDao(messageRecords)

    override val L get() = LocaleController.forChat(owner)

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

        database {

            messageRecords.dropStatement()
            UserBot.removeFromCache(userBot)
            UserBots.deleteWhere { UserBots.botId eq botUserId }

        }

        BotInstances.instanceMap.remove(botUserId)

    }

    override suspend fun onAuthorizationFailed(ex: TdException) {

        destroy()

        Launcher.apply {

            sudo make L.BOT_AUTH_FAILED.input(userBot.username) sendTo owner

        }

    }

    override suspend fun onLogout() {

        destroy()

        Launcher.apply {

            sudo make L.BOT_LOGOUT.input(userBot.username) sendTo owner

        }

    }

    override fun onLoad() {

        options databaseDirectory "data/pm/$botUserId"

        database {

            SchemaUtils.create(messageRecords)

        }

        addHandler(InputHandler(intArrayOf(owner), this))
        addHandler(OutputHandler(owner, this))
        addHandler(EditHandler(owner, this))
        addHandler(DeleteHandler(owner, this))
        addHandler(JoinHandler(owner, this))
        addHandler(SetStartMessages())

        initStartPayload("finish_creation")

    }

    override suspend fun onLaunch(userId: Int, chatId: Long, message: TdApi.Message) {

        if (userId != owner) {

            val startMessages = StartMessages.Cache.fetch(botUserId).value

            if (startMessages == null) {

                sudo makeHtml L.DEFAULT_WELCOME + "\n\n" + L.POWERED_BY.input(Launcher.me.username,L.LICENSE.input(Launcher.repoName, Launcher.licenseUrl, "Github Repo".toLink(Launcher.repoUrl))) sendTo chatId

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

            if (userId != owner) rejectFunction()

            sudo make L.CREATE_FINISHED sendTo chatId

        }

    }

}