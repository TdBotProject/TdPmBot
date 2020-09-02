package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getMessage
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.FN_PRIVATE_ONLY
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.ActionMessages
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.database.UserBots
import io.github.nekohasekai.pm.manage.menu.BotMenu
import td.TdApi

class MyBots : TdHandler() {

    companion object {

        const val command = "my_bots"

        const val dataId = DATA_EDIT_BOTS

        val DEF = TdApi.BotCommand(
                command,
                LocaleController.EDIT_BOTS_DEF
        )

    }

    override fun onLoad() {

        initFunction(command)

        initData(dataId)

        sudo addHandler BotMenu()

    }

    val actionMessages by lazy { KeyValueCacheMap(database, ActionMessages) }

    override suspend fun gc() {

        actionMessages.gc()

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (!message.fromPrivate) {

            sudo makeHtml LocaleController.FN_PRIVATE_ONLY onSuccess deleteDelay(message) replyTo message

            return

        }

        rootMenu(userId, chatId, 0L, false)

    }

    suspend fun deleteActionMessage(userId: Int,chatId: Long,messageId: Long) {

        val currentActionMessage = actionMessages.fetch(userId)

        val currentActionMessageId = currentActionMessage.value

        if (currentActionMessageId != null && currentActionMessageId != messageId) {

            runCatching {

                getMessage(chatId, currentActionMessageId)

                syncDelete(chatId, currentActionMessageId)

            }

        }

    }

    fun saveActionMessage(userId: Int,messageId: Long) {

        val currentActionMessage = actionMessages.fetch(userId)

        currentActionMessage.apply {

            value = messageId
            changed = true

        }

    }

    suspend fun rootMenu(userId: Int, chatId: Long, messageId: Long, isEdit: Boolean) {

        deleteActionMessage(userId, chatId, messageId)

        val bots = LinkedHashMap<String, Int>()

        if (chatId == Launcher.admin) {

            bots[me.username] = me.id

        }

        database {

            UserBot.find { UserBots.owner eq userId }.forEach {

                bots[it.username] = it.botId

            }

        }

        val L = LocaleController.forChat(userId)

        if (bots.isEmpty()) {

            sudo make L.NO_BOTS at messageId edit isEdit syncOrEditTo chatId

            return

        }

        sudo make L.SELECT_TO_SET withMarkup inlineButton {

            var line: InlineButtonBuilder.Line? = null

            bots.forEach {

                if (line == null) {

                    line = newLine()
                    line!!.dataButton("@${it.key}", BotMenu.dataId, it.value.toByteArray())

                } else {

                    line!!.dataButton("@${it.key}", BotMenu.dataId, it.value.toByteArray())
                    line = null

                }

            }

        } onSuccess {

           saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

        finishWithDelay(1500L)

    }

    override suspend fun onNewCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>) {

        sudo confirmTo queryId

        rootMenu(userId, chatId, messageId, true)

    }

}