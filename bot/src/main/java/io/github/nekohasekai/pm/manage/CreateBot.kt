package io.github.nekohasekai.pm.manage

import cn.hutool.core.util.NumberUtil
import com.pengrad.telegrambot.request.GetMe
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.FN_PRIVATE_ONLY
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.database.UserBot
import td.TdApi

class CreateBot : TdHandler() {

    private var persistId = PERSIST_BOT_CREATE

    override fun onLoad() {

        initFunction("new_bot")
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

        sudo make "Input bot token: " withMarkup TdApi.ReplyMarkupForceReply(true) sendTo chatId

        writePersist(userId, persistId, 0L)

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<ByteArray>) {

        createByToken(userId, chatId, message.text)

    }

    val String?.isTokenInvalid get() = this == null || length < 40 || length > 50 || !contains(":") || !NumberUtil.isInteger(substringBefore(":"))

    suspend fun createByToken(userId: Int, chatId: Long, token: String?) {

        if (token.isTokenInvalid) {

            sudo make "Invalid bot token." sendTo chatId

            return

        }

        removePersist(userId)

        val status = sudo make "Fetching info..." syncTo chatId

        val botMe = try {

            httpSync(token!!, GetMe()).user()

        } catch (e: TdException) {

            sudo make """
Invalid bot token: ${e.message}.

Type /cancel to cancel the operation.
""" syncEditTo status

            return

        }

        val exists = database { UserBot.findById(botMe.id()) }

        if (exists != null) {

            sudo make "Failed: bot alreay exists." syncEditTo status

            return

        }

        sudo make "Creating bot..." syncEditTo status

        val userBot = database {

            UserBot.new(botMe.id()) {

                botToken = token
                username = botMe.username()
                owner = userId

            }

        }

        if (BotInstances.initBot(userBot).waitForAuth()) {

            sudo makeHtml """
Click this link to complete the creation: https://t.me/${userBot.username}?start=finish_creation

${"If you see the \"Start\" button, click it.".asBlod}
""" syncEditTo status

        }


    }

}