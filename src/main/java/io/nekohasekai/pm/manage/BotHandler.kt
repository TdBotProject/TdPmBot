package io.nekohasekai.pm.manage

import io.nekohasekai.ktlib.core.escapeHtmlTags
import io.nekohasekai.ktlib.core.shift
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.extensions.asInt
import io.nekohasekai.ktlib.td.extensions.displayName
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.launcher

abstract class BotHandler : TdHandler() {

    fun botUserName(botUserId: Int, userBot: UserBot?): String {

        return if (botUserId == me.id) me.username else userBot!!.username

    }

    fun botName(botUserId: Int, userBot: UserBot?): String {

        return if (botUserId == me.id) me else {

            launcher.initBot(userBot!!).me

        }.displayName

    }

    fun botNameHtml(botUserId: Int, userBot: UserBot?): String {

        return if (botUserId == me.id) me else {

            launcher.initBot(userBot!!).me

        }.displayName.escapeHtmlTags()

    }

    fun findUserBot(botId: Int): UserBot? {

        return if (botId == me.id) null else database {

            UserBot.findById(botId)

        }
    }

    override suspend fun onNewCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>
    ) {

        val botId = data[0].asInt()

        val userBot = if (botId == me.id && chatId == launcher.admin) {

            null

        } else findUserBot(botId)

        if (userBot != null && userBot.owner != userId || botId != me.id && userBot == null) {

            findHandler<MyBots>().rootMenu(userId, chatId, messageId, true)

            return

        }

        onNewBotCallbackQuery(userId, chatId, messageId, queryId, data.shift(), botId, userBot)

    }

    abstract suspend fun onNewBotCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>,
        botUserId: Int,
        userBot: UserBot?
    )

}