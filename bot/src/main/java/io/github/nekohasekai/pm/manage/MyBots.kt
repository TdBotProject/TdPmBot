package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.L
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.database.UserBots
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

    }

    val actionMessages = hashMapOf<Int, Long>()

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        rootMenu(userId, chatId, 0L, false)

    }

    fun rootMenu(userId: Int, chatId: Long, messageId: Long, edit: Boolean) {

        val currentActionMessage = actionMessages[userId]

        if (currentActionMessage != null && currentActionMessage != messageId) {

            delete(chatId, currentActionMessage)

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

            sudo make L.NO_BOTS sendTo chatId

            return

        }

        sudo make L.SELECT_TO_SET withMarkup inlineButton {

            var line: InlineButtonBuilder.Line? = null

            bots.forEach {

                if (line == null) {

                    line = newLine()
                    line!!.dataButton("@$it", dataId, it.value.toByteArray())

                } else {

                    line!!.dataButton("@$it", dataId, it.value.toByteArray())
                    line = null

                }


            }

        } onSuccess {

            actionMessages[userId] = it.id

        } at messageId edit edit sendOrEditTo chatId

    }

    override suspend fun onNewCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>) {


    }

}