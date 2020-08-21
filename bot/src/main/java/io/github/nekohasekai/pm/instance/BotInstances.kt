package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.defaultLog
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.UserBot
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

val TdHandler.messagesForCurrentBot get() = (MessageRecords.botId eq me.id)

object BotInstances {

    val instanceMap = HashMap<Int, PmBot>()

    fun loadAll() {

        defaultLog.trace("Loading PM Bots")

        for (userBot in UserBot.all()) {

            defaultLog.trace("Loading @${userBot.username}")

            initBot(userBot)

        }

    }

    fun initBot(userBot: UserBot): PmBot {

        return instanceMap[userBot.botId] ?: synchronized(instanceMap) {

            PmBot(userBot.botToken, userBot).apply {

                instanceMap[botUserId] = this

                start()

            }

        }

    }

}