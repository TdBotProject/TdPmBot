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

class BotCreate : TdHandler() {

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

        startCreate(userId, chatId)

    }

    fun startCreate(userId: Int, chatId: Long) {

        sudo make "Input bot token: " sendTo chatId

        writePersist(userId, persistId, 0)

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Int) {

        removePersist(userId)

        createByToken(userId, chatId, message.text)

    }

    suspend fun createByToken(userId: Int, chatId: Long, token: String?) {

        if (token == null || token.length < 40 || token.length > 50 || !token.contains(":") || !NumberUtil.isInteger(token.substringBefore(":"))) {

            sudo make "Invalid bot token." sendTo chatId

            return

        }

        val botMe = try {

            httpSync(token, GetMe()).user()

        } catch (e: TdException) {

            sudo make "Invalid bot token: ${e.message}." sendTo chatId

            return

        }

        if (database { UserBot.findById(botMe.id()) } != null) {

            sudo make "Failed: alreay exists." sendTo chatId

            return

        }

        val userBot = database {

            UserBot.new(botMe.id()) {

                botToken = token
                owner = userId

            }

        }

        BotInstances.initBot(userBot)
    }

}