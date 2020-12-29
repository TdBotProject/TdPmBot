package io.nekohasekai.pm.manage.menu

import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.extensions.asByteArray
import io.nekohasekai.ktlib.td.extensions.asInputOrForward
import io.nekohasekai.ktlib.td.extensions.userCalled
import io.nekohasekai.ktlib.td.i18n.*
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.manage.BotHandler
import io.nekohasekai.pm.manage.MyBots
import td.TdApi
import java.util.*

class StartMessagesMenu : BotHandler() {

    companion object {

        const val dataId = DATA_EDIT_STARTS_MESSAGES
        const val persistId = PERSIST_SET_START_MESSAGES

    }

    override fun onLoad() {

        if (sudo is TdPmBot) initData(dataId)

        initPersist(persistId)

    }

    suspend fun startMessagesMenu(
        userId: Int,
        chatId: Long,
        messageId: Long,
        isEdit: Boolean,
        botUserId: Int,
        userBot: UserBot?
    ) {

        val L = localeFor(userId)

        val startMessages = launcher.startMessages.fetch(botUserId).value

        sudo make L.START_MESSAGES_STATUS.input(
            botName(botUserId, userBot), botUserName(botUserId, userBot), when {

                startMessages == null -> L.SETTING_UNDEF
                startMessages.isEmpty() -> L.EMPTY
                else -> L.MESSAGES_STATUS_COUNT.input(startMessages.size)

            }
        ) withMarkup inlineButton {

            val botId = botUserId.asByteArray()

            newLine {

                dataButton(L.EDIT, dataId, botId, byteArrayOf(0))

                if (startMessages != null) {

                    dataButton(L.RESET, dataId, botId, byteArrayOf(1))

                }

            }

            dataLine(L.BACK_ARROW, BotMenu.dataId, botId)

        } onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>,
        botUserId: Int,
        userBot: UserBot?
    ) {

        sudo confirmTo queryId

        val L = localeFor(userId)

        if (data.isEmpty()) {

            startMessagesMenu(userId, chatId, messageId, true, botUserId, userBot)

        } else when (data[0][0].toInt()) {

            -1 -> {

                if (sudo.persists.read(userId)?.persistId == persistId) {

                    sudo removePersist userId

                    onSendCanceledMessage(userId, chatId)

                }

                startMessagesMenu(userId, chatId, messageId, true, botUserId, userBot)

            }

            0 -> {

                if (userBot == null) {

                    sudo make L.INPUT_MESSAGES withMarkup inlineButton {

                        dataLine(L.BACK_ARROW, dataId, botUserId.asByteArray(), byteArrayOf(-1))

                    } onSuccess {

                        writePersist(userId, persistId, 0, StartMessagesCache(me.id), it.id, allowFunction = true)

                    } at messageId editTo chatId

                } else {

                    launcher.initBot(userBot).apply {

                        writePersist(userId, persistId, 0, StartMessagesCache(me.id), allowFunction = true)

                        sudo make L.INPUT_MESSAGES sendTo chatId

                    }

                    sudo make L.JUMP_TO_SET.input(userBot.username) withMarkup inlineButton {

                        dataLine(L.BACK_ARROW, dataId, botUserId.asByteArray(), byteArrayOf(-1))

                    } at messageId editTo chatId

                }

            }

            1 -> {

                launcher.startMessages.fetch(botUserId).apply {

                    value = null
                    changed = true

                    flush()

                }

                sudo makeAnswer L.MESSAGES_RESET answerTo queryId

                startMessagesMenu(userId, chatId, messageId, true, botUserId, userBot)

            }

        }

    }

    override suspend fun onPersistMessage(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        subId: Int,
        data: Array<Any?>
    ) {

        if (subId == 0) {

            val L = localeFor(userId)

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

    override suspend fun onPersistCancel(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        subId: Int,
        data: Array<Any?>
    ) {

        if (data.size > 1) {

            val messageId = data[1] as Long

            delete(chatId, messageId)

            startMessagesMenu(userId, chatId, messageId, false, me.id, null)

        }

    }

    override suspend fun onPersistFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        subId: Int,
        data: Array<Any?>,
        function: String,
        param: String,
        params: Array<String>,
        originParams: Array<String>
    ) {

        if (function == "preview") {

            val L = localeFor(userId)

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

            val L = localeFor(userId)

            val cache = data[0] as StartMessagesCache

            val botUserId = cache.botId

            if (chatId != launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                sudo removePersist userId

                onSendTimeoutMessage(userId, chatId)

                return

            }

            userCalled(userId, "submitted start messages to $botUserId")

            launcher.startMessages.fetch(botUserId).apply {

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