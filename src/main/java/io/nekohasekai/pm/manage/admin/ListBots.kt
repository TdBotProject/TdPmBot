package io.nekohasekai.pm.manage.admin

import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.raw.getUserOrNull
import io.nekohasekai.ktlib.td.extensions.displayName
import io.nekohasekai.ktlib.td.extensions.htmlCode
import io.nekohasekai.ktlib.td.extensions.htmlDisplayExpanded
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.manage.global
import td.TdApi
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class ListBots : AdminCommand() {

    override fun onLoad() {
        initFunction("list_bots")
    }

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>
    ) {
        super.onFunction(userId, chatId, message, function, param, params)

        class BotsByUser(
            val userId: Int,
        ) : Comparable<BotsByUser> {
            val bots = HashSet<UserBot>()
            override fun compareTo(other: BotsByUser): Int {
                if (this == other) return 0
                val size = other.bots.size - bots.size
                if (size != 0) return size
                return other.userId - userId
            }
        }

        val allBots = database { UserBot.all().toList() }
        val botsMap = HashMap<Int, BotsByUser>()
        for (userBot in allBots) {
            botsMap.getOrPut(userBot.owner) { BotsByUser(userBot.owner) }.bots.add(userBot)
        }
        val botsList = TreeSet(botsMap.values)

        var content = "All Bots: " + allBots.size + "\n\n"

        var count = 0
        for (botsByUser in botsList) {
            val owner = getUserOrNull(botsByUser.userId)

            content += "■ "
            if (owner == null) {
                content += botsByUser.userId.htmlCode + " (Unknown)"
            } else if (owner.type is TdApi.UserTypeDeleted) {
                content += botsByUser.userId.htmlCode + " (Deleted)"
            } else {
                content += owner.htmlDisplayExpanded
                if (owner.profilePhoto == null && owner.status is TdApi.UserStatusEmpty) {
                    content += " (Mostly Blocked By)"
                }
            }
            content += " :\n"

            for (userBot in botsByUser.bots) {
                content += global.instanceMap[userBot.botId]?.me?.displayName?.htmlCode ?: "Unknown"
                content += " (@" + userBot.username + " ${userBot.botId.htmlCode} )"
                content += "\n"
            }

            content += "\n"

            count += botsByUser.bots.size
            if (count >= 10) {
                sudo makeHtml content sendTo chatId
                count = 0
                content = ""
            }
        }

        if (content.isNotBlank()) sudo makeHtml content sendTo chatId

    }

}