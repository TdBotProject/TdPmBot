package io.nekohasekai.pm.manage.admin

import cn.hutool.core.util.NumberUtil
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.extensions.htmlDisplayExpanded
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.database.UserBots
import io.nekohasekai.pm.manage.global
import td.TdApi

class DeleteBots : AdminCommand() {

    override fun onLoad() {
        initFunction("_delete_bots")
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
            sudo make "/_delete_bots <botId/username>..." replyTo message
            return
        }

        for (botToDelete in params) {
            val userBot = database {
                if (NumberUtil.isInteger(botToDelete)) {
                    UserBot.find { UserBots.owner eq botToDelete.toInt() }.firstOrNull()
                } else {
                    UserBot.find { UserBots.username eq botToDelete }.firstOrNull()
                }
            }
            if (userBot == null) {
                sudo make "Bot botToDelete not found" replyTo message
                continue
            }
            val bot = global.initBot(userBot)
            bot.destroy()

            sudo makeHtml  "Bot " + bot.me.htmlDisplayExpanded + " has been deleted" replyTo message
        }
    }

}