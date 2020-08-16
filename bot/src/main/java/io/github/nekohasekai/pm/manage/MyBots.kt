package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.FN_PRIVATE_ONLY
import io.github.nekohasekai.nekolib.i18n.L
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.ActionMessages
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.database.UserBots
import io.github.nekohasekai.pm.manage.menu.BotEdits
import td.TdApi

class MyBots : TdHandler() {

    companion object {

        const val command = "my_bots"

        const val dataId = DATA_EDIT_BOTS

        val DEF = TdApi.BotCommand(
                command,
                L.EDIT_BOTS_DEF
        )

    }

    override fun onLoad() {

        initFunction(command)

        initData(dataId)

        sudo addHandler BotEdits()

    }

    val actionMessages by lazy { KeyValueCacheMap(database, ActionMessages) }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (!message.fromPrivate) {

            sudo make LocaleController.FN_PRIVATE_ONLY onSuccess deleteDelay(message) replyTo message

            return

        }

        rootMenu(userId, chatId, 0L, false)

    }

    fun rootMenu(userId: Int, chatId: Long, messageId: Long, isEdit: Boolean) {

        val currentActionMessage = actionMessages.fetch(userId)

        val currentActionMessageId = currentActionMessage.value

        if (currentActionMessageId != null && currentActionMessageId != messageId) {

            delete(chatId, currentActionMessageId)

        }

        val bots = LinkedHashMap<String, Int>()

        if (chatId == Launcher.admin) {

            bots[me.username] = me.id

        }

        database {

            UserBot.find { UserBots.owner eq userId }.forEach {

                bots[it.username] = it.botId

            }

        }

        val L = L.forChat(userId)

        if (bots.isEmpty()) {

            sudo make L.NO_BOTS at messageId edit isEdit sendOrEditTo chatId

            return

        }

        sudo make L.SELECT_TO_SET withMarkup inlineButton {

            var line: InlineButtonBuilder.Line? = null

            bots.forEach {

                if (line == null) {

                    line = newLine()
                    line!!.dataButton("@${it.key}", BotEdits.dataId, it.value.toByteArray())

                } else {

                    line!!.dataButton("@${it.key}", BotEdits.dataId, it.value.toByteArray())
                    line = null

                }

            }

        } onSuccess {

            currentActionMessage.value = it.id
            currentActionMessage.changed = true

        } at messageId edit isEdit sendOrEditTo chatId

    }

    override suspend fun onNewCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>) {

        sudo confirmTo queryId

        rootMenu(userId, chatId, messageId, true)

    }

}