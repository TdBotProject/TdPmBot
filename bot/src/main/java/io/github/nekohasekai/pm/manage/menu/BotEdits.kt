package io.github.nekohasekai.pm.manage.menu

import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.L
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.manage.BotHandler
import io.github.nekohasekai.pm.manage.MyBots

class BotEdits : BotHandler() {

    companion object {

        const val dataId = DATA_EDIT_BOT

    }

    override fun onLoad() {

        initData(dataId)

        sudo addHandler StartMessageEdits()
        sudo addHandler IntegrationEdits()

    }

    fun botMenu(userId: Int, chatId: Long, messageId: Long, isEdit: Boolean, botUserId: Int, userBot: UserBot?) {

        val L = L.forChat(userId)

        sudo make L.BOT_EDITS.input(userBot?.username ?: me.username) withMarkup inlineButton {

            dataLine(L.MENU_START_MESSAGES, StartMessageEdits.dataId, botUserId.toByteArray())
            dataLine(L.MENU_INTEGRATION, IntegrationEdits.dataId, botUserId.toByteArray())

            dataLine(L.MENU_BACK_TO_BOT_LIST, MyBots.dataId)

        } at messageId edit isEdit sendOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        sudo confirmTo queryId

        botMenu(userId, chatId, messageId, true, botUserId, userBot)

    }

}