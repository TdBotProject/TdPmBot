package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.makeAlert
import io.github.nekohasekai.nekolib.core.utils.shift
import io.github.nekohasekai.nekolib.core.utils.toInt
import io.github.nekohasekai.nekolib.i18n.L
import io.github.nekohasekai.pm.INVALID_SELECTED
import io.github.nekohasekai.pm.Launcher
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.manage.MyBots

abstract class BotHandler : TdHandler() {

    override suspend fun onNewCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>) {

        val botId = data[0].toInt()

        val userBot = if (botId == me.id && chatId == Launcher.admin) {

            null

        } else database {

            UserBot.findById(botId)

        }

        val L = L.forChat(userId)

        if (userBot != null && userBot.owner != userId) {

            findHandler<MyBots>().rootMenu(userId, chatId, messageId, true)

            return

        }

        onNewBotCallbackQuery(userId, chatId, messageId, queryId, data.shift(), botId, userBot)

    }

    abstract suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?)

}