package io.nekohasekai.pm.manage.menu

import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.core.extensions.asByteArray
import io.nekohasekai.ktlib.td.core.utils.*
import io.nekohasekai.ktlib.td.i18n.BACK_ARROW
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.instance.BotInstances
import io.nekohasekai.pm.manage.BotHandler
import io.nekohasekai.pm.manage.MyBots
import java.util.*

class DeleteMenu : BotHandler() {

    companion object {

        const val dataId = DATA_DELETE_BOT_MENU

    }

    override fun onLoad() {

        initData(dataId)

    }

    suspend fun botDeleteMenu(botUserId: Int, userBot: UserBot?, userId: Int, chatId: Long, messageId: Long, isEdit: Boolean, again: Boolean) {

        val L = localeFor(userId)

        sudo makeHtml (if (!again) L.MENU_BOT_DELETE_CONFIRM else L.MENU_BOT_DELETE_CONFIRM_AGAIN).input(

                botNameHtml(botUserId, userBot),
                botUserName(botUserId, userBot)

        ) withMarkup inlineButton {

            val botId = botUserId.asByteArray()

            if (!again) {

                dataLine(L.MENU_BOT_DEL_NO_1, BotMenu.dataId, botId)
                dataLine(L.MENU_BOT_DEL_NO_2, BotMenu.dataId, botId)
                dataLine(L.MENU_BOT_DEL_YES_1, dataId, botId, byteArrayOf(0))

            } else {

                dataLine(L.MENU_BOT_DEL_NO_3, BotMenu.dataId, botId)
                dataLine(L.MENU_BOT_DEL_NO_4, BotMenu.dataId, botId)
                dataLine(L.MENU_BOT_DEL_YES_2, dataId, botId, byteArrayOf(1))

            }

            Collections.shuffle(this)

            dataLine(L.BACK_ARROW, BotMenu.dataId, botId)

        } onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        sudo confirmTo queryId

        if (data.isEmpty()) {

            botDeleteMenu(botUserId, userBot, userId, chatId, messageId, isEdit = true, again = false)

        } else when (data[0][0].toInt()) {

            0 -> botDeleteMenu(botUserId, userBot, userId, chatId, messageId, isEdit = true, again = true)

            1 -> {

                val L = localeFor(userId)

                sudo make Typing sendTo chatId

                val status = sudo make L.STOPPING at messageId syncEditTo chatId

                val bot = BotInstances.initBot(userBot!!)

                bot.waitForClose()

                sudo make Typing sendTo chatId

                sudo make L.DELETING editTo status

                bot.destroy()

                sudo make L.BOT_DELETED editTo status

            }

        }

    }

}