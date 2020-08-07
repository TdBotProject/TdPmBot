package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.pm.database.UserBot

object BotInstances {

    val instanceMap = HashMap<Int, PmBot>()

    fun loadAll() {

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