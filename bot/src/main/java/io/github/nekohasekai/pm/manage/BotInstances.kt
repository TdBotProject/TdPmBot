package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.Launcher
import io.github.nekohasekai.nekolib.core.utils.invoke
import io.github.nekohasekai.pm.database.UserBot

object BotInstances {

    val instanceMap = HashMap<Int, PmBot>()

    fun loadAll() = Launcher.database {

        UserBot.all().forEach { initBot(it) }

    }

    fun initBot(userBot: UserBot): PmBot {

        return instanceMap[userBot.botId] ?: synchronized(instanceMap) {

            PmBot(userBot.botToken, userBot.owner).apply {

                instanceMap[botUserId] = this

                start()

            }

        }

    }

}