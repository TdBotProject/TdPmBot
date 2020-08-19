package io.github.nekohasekai.pm.manage.menu

import cn.hutool.http.HtmlUtil
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.BACK_ARROW
import io.github.nekohasekai.nekolib.i18n.L
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances
import io.github.nekohasekai.pm.manage.BotHandler
import java.util.*

class DeleteMenu : BotHandler() {

    companion object {

        const val dataId = DATA_DELETE_BOT_MENU

    }

    override fun onLoad() {

        initData(dataId)

    }

    fun botDeleteMenu(botUserId: Int, userBot: UserBot?, userId: Int, chatId: Long, messageId: Long, isEdit: Boolean, again: Boolean) {

        val L = L.forChat(userId)

        sudo makeHtml (if (!again) L.MENU_BOT_DELETE_CONFIRM else L.MENU_BOT_DELETE_CONFIRM_AGAIN).input(

                botNameHtml(botUserId, userBot),
                botUserName(botUserId, userBot)

        ) withMarkup inlineButton {

            if (!again) {

                dataLine(L.MENU_BOT_DEL_NO_1, BotMenu.dataId, botUserId.toByteArray())
                dataLine(L.MENU_BOT_DEL_NO_2, BotMenu.dataId, botUserId.toByteArray())
                dataLine(L.MENU_BOT_DEL_YES_1, dataId, botUserId.toByteArray(), 0.toByteArray())

            } else {

                dataLine(L.MENU_BOT_DEL_NO_3, BotMenu.dataId, botUserId.toByteArray())
                dataLine(L.MENU_BOT_DEL_NO_4, BotMenu.dataId, botUserId.toByteArray())
                dataLine(L.MENU_BOT_DEL_YES_2, dataId, botUserId.toByteArray(), 1.toByteArray())

            }

            Collections.shuffle(this)

            dataLine(L.BACK_ARROW, BotMenu.dataId, botUserId.toByteArray())

        } at messageId edit isEdit sendOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        sudo confirmTo queryId

        if (data.isEmpty()) {

            botDeleteMenu(botUserId, userBot, userId, chatId, messageId, isEdit = true, again = false)

        } else when (data[0].toInt()) {

            0 -> botDeleteMenu(botUserId, userBot, userId, chatId, messageId, isEdit = true, again = true)

            1 -> {

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