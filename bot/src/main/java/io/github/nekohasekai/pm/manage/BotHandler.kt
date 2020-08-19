package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.displayName
import io.github.nekohasekai.nekolib.core.utils.displayNameHtml
import io.github.nekohasekai.nekolib.core.utils.shift
import io.github.nekohasekai.nekolib.core.utils.toInt
import io.github.nekohasekai.nekolib.i18n.L
import io.github.nekohasekai.pm.Launcher
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances

abstract class BotHandler : TdHandler() {

    fun botUserName(botUserId: Int, userBot: UserBot?): String {

        return if (botUserId == me.id) me.username else userBot!!.username

    }

    fun botName(botUserId: Int, userBot: UserBot?): String {

        return if (botUserId == me.id) me else {

            BotInstances.initBot(userBot!!).me

        }.displayName

    }

    fun botNameHtml(botUserId: Int, userBot: UserBot?): String {

        return if (botUserId == me.id) me else {

            BotInstances.initBot(userBot!!).me

        }.displayNameHtml

    }

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