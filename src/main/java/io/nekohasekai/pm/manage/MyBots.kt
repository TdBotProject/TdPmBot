package io.nekohasekai.pm.manage

import cn.hutool.core.date.SystemClock
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.core.raw.getMessage
import io.nekohasekai.ktlib.td.extensions.asByteArray
import io.nekohasekai.ktlib.td.extensions.fromPrivate
import io.nekohasekai.ktlib.td.i18n.FN_PRIVATE_ONLY
import io.nekohasekai.ktlib.td.i18n.clientLocale
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.ActionMessage
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.database.UserBots
import io.nekohasekai.pm.manage.menu.BotMenu
import td.TdApi

class MyBots : TdHandler() {

    companion object {

        const val command = "my_bots"

        const val dataId = DATA_EDIT_BOTS

    }

    fun def() = TdApi.BotCommand(
        command,
        clientLocale.EDIT_BOTS_DEF
    )

    override fun onLoad() {

        initFunction(command)

        initData(dataId)

        sudo addHandler BotMenu()

    }

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>
    ) {

        if (!message.fromPrivate) {

            sudo makeHtml clientLocale.FN_PRIVATE_ONLY onSuccess deleteDelay(message) replyTo message

            return

        }

        rootMenu(userId, chatId, 0L, false)

    }

    suspend fun deleteActionMessage(userId: Int, chatId: Long, messageId: Long) {

        val currentActionMessage = launcher.actionMessages.fetch(userId)

        val currentActionMessageId = currentActionMessage.value?.messageId

        if (currentActionMessageId != null && currentActionMessageId != messageId) {

            runCatching {

                getMessage(chatId, currentActionMessageId)

                syncDelete(chatId, currentActionMessageId)

            }

        }

    }

    fun saveActionMessage(userId: Int, messageId: Long) {

        val currentActionMessage = launcher.actionMessages.fetch(userId)

        currentActionMessage.apply {

            val currentMessage = value

            database.write {

                if (currentMessage == null) {

                    value = ActionMessage.new(userId) {

                        this.messageId = messageId
                        this.createAt = (SystemClock.now() / 1000L).toInt()

                    }

                } else {

                    currentMessage.messageId = messageId
                    currentMessage.createAt = (SystemClock.now() / 1000L).toInt()

                    changed = true

                }

            }

        }

    }

    suspend fun rootMenu(userId: Int, chatId: Long, messageId: Long, isEdit: Boolean) {

        deleteActionMessage(userId, chatId, messageId)

        val bots = LinkedHashMap<String, Int>()

        if (chatId == launcher.admin) {

            bots[me.username] = me.id

        }

        database {

            UserBot.find { UserBots.owner eq userId }.forEach {

                bots[it.username] = it.botId

            }

        }

        val L = localeFor(userId)

        if (bots.isEmpty()) {

            sudo make L.NO_BOTS at messageId edit isEdit syncOrEditTo chatId

            return

        }

        sudo make L.SELECT_TO_SET withMarkup inlineButton {

            var line: InlineButtonBuilder.Line? = null

            bots.forEach {

                if (line == null) {

                    line = newLine()
                    line!!.dataButton("@${it.key}", BotMenu.dataId, it.value.asByteArray())

                } else {

                    line!!.dataButton("@${it.key}", BotMenu.dataId, it.value.asByteArray())
                    line = null

                }

            }

        } onSuccess {

            saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

        finishWithDelay(1500L)

    }

    override suspend fun onNewCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>
    ) {

        sudo confirmTo queryId

        rootMenu(userId, chatId, messageId, true)

    }

}