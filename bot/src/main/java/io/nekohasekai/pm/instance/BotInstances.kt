package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.core.defaultLog
import io.nekohasekai.pm.database.UserBot

object BotInstances {

    val instanceMap = HashMap<Int, PmBot>()

    fun loadAll() {

        defaultLog.trace("Loading PM Bots")

        UserBot.all().forEach { initBot(it) }

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