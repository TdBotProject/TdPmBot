package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.utils.defaultLog
import io.github.nekohasekai.pm.Launcher
import io.github.nekohasekai.pm.database.UserBot

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