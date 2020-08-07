package io.github.nekohasekai.pm.manage

import cn.hutool.core.util.NumberUtil
import com.pengrad.telegrambot.request.GetMe
import io.github.nekohasekai.nekolib.core.client.TdClient
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.FN_PRIVATE_ONLY
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances
import td.TdApi

class CreateBot(val command: String = "new_bot") : TdHandler() {

    val DEF = TdApi.BotCommand(
            command,
            LocaleController.CREATE_BOT_DEF
    )

    private var persistId = PERSIST_BOT_CREATE

    override fun onLoad() {

        initFunction(command)
        initPersist(persistId)

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (!message.fromPrivate) {

            sudo make LocaleController.FN_PRIVATE_ONLY replyTo message send deleteDelay(message)

            return

        }

        if (param.isTokenInvalid) {

            startCreate(userId, chatId)

        } else {

            createByToken(userId, chatId, param)

        }

    }

    fun startCreate(userId: Int, chatId: Long) {

        val L = LocaleController.forChat(userId)

        sudo makeMd L.INPUT_BOT_TOKEN withMarkup TdApi.ReplyMarkupForceReply(true) sendTo chatId

        writePersist(userId, persistId, 0L)

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        createByToken(userId, chatId, message.text)

    }

    val String?.isTokenInvalid get() = this == null || length < 40 || length > 50 || !contains(":") || !NumberUtil.isInteger(substringBefore(":"))

    suspend fun createByToken(userId: Int, chatId: Long, token: String?) {

        val L = LocaleController.forChat(userId)

        if (token.isTokenInvalid) {

            sudo make L.INVALID_BOT_TOKEN.input("") sendTo chatId

            return

        }

        removePersist(userId)

        val status = sudo make L.FETCHING_INFO syncTo chatId

        sudo make Typing sendTo chatId

        val botMe = try {

            httpSync(token!!, GetMe()).user()

        } catch (e: TdException) {

            sudo make L.INVALID_BOT_TOKEN.input(" (${e.message})") editTo status

            return

        }

        val exists = TdClient.clients.any { botMe.id() == it.me.id } || database { UserBot.findById(botMe.id()) } != null

        if (exists) {

            sudo make L.failed { ALREADY_EXISTS } syncEditTo status

            sudo make CancelChatAction syncTo chatId

            return

        }

        sudo make Typing sendTo chatId

        sudo make L.CREATING_BOT editTo status

        val userBot = database {

            UserBot.new(botMe.id()) {

                botToken = token
                username = botMe.username()
                owner = userId

            }

        }

        if (BotInstances.initBot(userBot).waitForAuth()) {

            sudo makeHtml L.FINISH_CREATION.input(userBot.username) editTo status

        } else {

            defaultLog.warn("Pm bot failed when start")

            // TODO: handle bot stop

        }


    }

}