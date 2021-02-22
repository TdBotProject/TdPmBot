package io.nekohasekai.pm.manage.admin

import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.extensions.htmlDisplayExpanded
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.database.UserBots
import io.nekohasekai.pm.manage.global
import td.TdApi

class DeleteBotsByUser : AdminCommand() {

    override fun onLoad() {
        initFunction("_delete_bots_by_user")
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

        if (params.isEmpty()) {
            sudo make "/_delete_bots_by_user <userId>..." replyTo message
            return
        }

        val userIdsToDelete = try {
            params.map { it.toInt() }
        } catch (e: Exception) {
            sudo make "Invalid userIds" replyTo message
            return
        }

        for (userIdToDelete in userIdsToDelete) {
            val userBots = database { UserBot.find { UserBots.owner eq userIdToDelete }.toList() }
            for (userBot in userBots) {
                val bot = global.initBot(userBot).apply { me }
                bot.destroy()
                sudo makeHtml "Bot " + bot.me.htmlDisplayExpanded + " has been deleted" replyTo message
            }
        }
    }

}