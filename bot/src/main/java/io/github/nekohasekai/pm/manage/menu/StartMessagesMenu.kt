package io.github.nekohasekai.pm.manage.menu

import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.*
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.StartMessages
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances
import io.github.nekohasekai.pm.manage.BotHandler
import td.TdApi
import java.util.*

class StartMessagesMenu : BotHandler() {

    companion object {

        const val dataId = DATA_EDIT_STARTS_MESSAGES

        const val persistId = PERSIST_SET_START_MESSAGES

    }

    override fun onLoad() {

        if (sudo is Launcher) initData(dataId)

        initPersist(persistId)

    }

    fun startMessagesMenu(userId: Int, chatId: Long, messageId: Long, isEdit: Boolean, botUserId: Int, userBot: UserBot?) {

        val L = L.forChat(userId)

        val startMessages = StartMessages.Cache.fetch(botUserId).value

        sudo make L.START_MESSAGES_STATUS.input(botName(botUserId, userBot), botUserName(botUserId, userBot), when {

            startMessages == null -> L.SETTING_UNDEF
            startMessages.isEmpty() -> L.EMPTY
            else -> L.MESSAGES_STATUS_COUNT.input(startMessages.size)

        }) withMarkup inlineButton {

            newLine {

                dataButton(L.EDIT, dataId, botUserId.toByteArray(), 1.toByteArray())

                if (startMessages != null) {

                    dataButton(L.RESET, dataId, botUserId.toByteArray(), 2.toByteArray())

                }

            }

            dataLine(L.BACK_ARROW, BotMenu.dataId, botUserId.toByteArray())

        } at messageId edit isEdit sendOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        sudo confirmTo queryId

        val L = L.forChat(userId)

        if (data.isEmpty()) {

            startMessagesMenu(userId, chatId, messageId, true, botUserId, userBot)

        } else when (data[0].toInt()) {

            -1 -> {

                if (sudo.persists.fetch(userId).tdPersist?.persistId == persistId) {

                    sudo removePersist userId

                    onSendCanceledMessage(userId, chatId)

                }

                startMessagesMenu(userId, chatId, messageId, true, botUserId, userBot)

            }

            1 -> {

                if (userBot == null) {

                    sudo make L.INPUT_MESSAGES withMarkup inlineButton {

                        dataLine(L.BACK_ARROW, dataId, botUserId.toByteArray(), (-1).toByteArray())

                    } onSuccess {

                        writePersist(userId, persistId, 0L, StartMessagesCache(me.id), it.id, allowFunction = true)

                    } at messageId editTo chatId

                } else {

                    BotInstances.initBot(userBot).apply {

                        writePersist(userId, persistId, 0L, StartMessagesCache(me.id), allowFunction = true)

                        sudo make L.INPUT_MESSAGES sendTo chatId

                    }

                    sudo make L.JUMP_TO_SET.input(userBot.username) withMarkup inlineButton {

                        dataLine(L.BACK_ARROW, dataId, botUserId.toByteArray(), (-1).toByteArray())

                    } at messageId editTo chatId

                }

            }

            2 -> {

                StartMessages.Cache.fetch(botUserId).apply {

                    value = null
                    changed = true

                    flush()

                }

                sudo makeAnswer L.MESSAGES_RESET answerTo queryId

                startMessagesMenu(userId, chatId, messageId, true, botUserId, userBot)

            }

        }

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        if (subId == 0L) {

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

    override suspend fun onPersistCancel(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        if (data.size > 1) {

            val messageId = data[1] as Long

            delete(chatId, messageId)

            startMessagesMenu(userId, chatId, messageId, false, me.id, null)

        }

    }

    override suspend fun onPersistFunction(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (function == "preview") {

            val L = L.forChat(userId)

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

            val L = L.forChat(userId)

            val cache = data[0] as StartMessagesCache

            val botUserId = cache.botId

            if (chatId != Launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                userCalled(userId, "submit start messages to outdated bot")

                sudo make L.INVALID_SELECTED sendTo chatId

                return

            }

            userCalled(userId, "submitted start messages to $botUserId")

            StartMessages.Cache.fetch(botUserId).apply {

                value = cache.messages
                changed = true

                flush()

            }

            sudo make L.SETTING_SAVED sendTo chatId

            if (data.size > 1) {

                val messageId = data[1] as Long

                delete(chatId, messageId)

                startMessagesMenu(userId, chatId, messageId, false, me.id, null)

            }

        } else if (function == "reset") {

            sudo removePersist userId

            val L = L.forChat(userId)

            val cache = data[0] as StartMessagesCache

            val botUserId = cache.botId

            if (chatId != Launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                userCalled(userId, "submit start messages to outdated bot")

                sudo make L.INVALID_SELECTED sendTo chatId

                return

            }

            userCalled(userId, "reset start messages to $botUserId")


        } else {

            rejectFunction()

        }

    }

    class StartMessagesCache(

            val botId: Int,
            val messages: LinkedList<TdApi.InputMessageContent> = LinkedList()

    ) {

        constructor() : this(0)

    }

}