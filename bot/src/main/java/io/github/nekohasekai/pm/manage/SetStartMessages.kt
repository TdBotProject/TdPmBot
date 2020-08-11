package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.LICENSE
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.nekolib.i18n.SETTING_SAVED
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.StartMessages
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances
import td.TdApi
import java.util.*

class SetStartMessages : UserBotSelector(true) {

    val dataId = DATA_SET_START_MESSAGES

    override val persistId = PERSIST_SET_START_MESSAGES

    val function = "set_start_messages"

    val DEF = TdApi.BotCommand(
            function,
            LocaleController.SET_STARTS_DEF
    )

    override fun onLoad() {

        super.onLoad()

        if (sudo is Launcher) {

            initFunction(function)

            initData(dataId)

        }

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        userCalled(userId, "start select bot to select")

        val L = LocaleController.forChat(userId)

        doSelect(L, userId, 0L, L.SELECT_TO_SET)

    }

    class StartMessagesCache(

            val botId: Int,
            val messages: LinkedList<TdApi.InputMessageContent> = LinkedList()

    ) {

        constructor(): this(0)

    }

    override suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?) {

        val L = LocaleController.forChat(userId)

        if (userBot == null) {

            userCalled(userId, "set launcher`s start messages")

            startSet(L, userId, chatId)

            return

        }

        userCalled(userId, "set @${userBot.username} start messages")

        BotInstances.initBot(userBot).findHandler<SetStartMessages>().startSet(L, userId, chatId)

        sudo make L.JUMP_TO_SET.input(userBot.username) removeKeyboard true sendTo chatId

    }

    suspend fun startSet(L: LocaleController, userId: Int, chatId: Long) {

        val cache = StartMessagesCache(me.id)

        writePersist(userId, persistId, 1L, cache, allowFunction = true)

        val startMessages = StartMessages.Cache.fetch(me.id).value

        if (startMessages == null) {

            sudo make L.SET_MESSAGES_STATUS.input(L.SETTING_UNDEF) sendTo chatId

        } else if (startMessages.isEmpty()) {

            sudo make L.SET_MESSAGES_STATUS.input(L.EMPTY) sendTo chatId

        } else {

            sudo make L.SET_MESSAGES_STATUS.input(L.MESSAGES_STATUS_COUNT.input(startMessages.size)) sendTo chatId

            try {

                cache.messages.forEach {

                    sudo make it syncTo chatId

                }

            } catch (e: TdException) {

                sudo make L.ERROR_IN_PREVIEW syncTo chatId

            }

        }

        sudo make L.INPUT_MESSAGES removeKeyboard true syncTo chatId

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        super.onPersistMessage(userId, chatId, message, subId, data)

        if (subId == 1L) {

            userCalled(userId, "added message ${message.content.javaClass.simpleName.substringAfter("Message")}")

            val L = LocaleController.forChat(userId)

            val cache = data[0] as StartMessagesCache

            val content = message.asInputOrForward

            cache.messages.add(content)

            if (content is TdApi.InputMessageForwarded) {

                sudo make L.MESSAGE_ADDED_FWD sendTo chatId

            } else {

                sudo make L.MESSAGE_ADDED sendTo chatId

            }

        }

    }

    override suspend fun onPersistFunction(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (function == "preview") {

            val L = LocaleController.forChat(userId)

            val cache = data[0] as StartMessagesCache

            if (cache.messages.isEmpty()) {

                sudo make L.EMPTY sendTo chatId

            } else {

                try {

                    cache.messages.forEach {

                        sudo make it syncTo chatId

                    }

                } catch (e: TdException) {

                    sudo make L.ERROR_IN_PREVIEW sendTo chatId

                }

            }

        } else if (function == "submit") {

            sudo removePersist userId

            val L = LocaleController.forChat(userId)

            val cache = data[0] as StartMessagesCache

            val botUserId = cache.botId

            if (chatId != Launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                userCalled(userId, "submit start messages to outdated bot")

                sudo make L.INVALID_SELECTED sendTo chatId

                return

            }

            userCalled(userId, "submitted start messages to $botUserId")

            val column = StartMessages.Cache.fetch(botUserId)

            column.value = cache.messages
            column.changed = true

            StartMessages.Cache.remove(botUserId)

            sudo make L.SETTING_SAVED sendTo chatId

        } else if (function == "reset") {

            sudo removePersist userId

            val L = LocaleController.forChat(userId)

            val cache = data[0] as StartMessagesCache

            val botUserId = cache.botId

            if (chatId != Launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                userCalled(userId, "submit start messages to outdated bot")

                sudo make L.INVALID_SELECTED sendTo chatId

                return

            }

            userCalled(userId, "reset start messages to $botUserId")

            val column = StartMessages.Cache.fetch(botUserId)

            column.value = null
            column.changed = true

            StartMessages.Cache.remove(botUserId)

            sudo make L.MESSAGES_RESET sendTo chatId

        } else {

            rejectFunction()

        }

    }

}