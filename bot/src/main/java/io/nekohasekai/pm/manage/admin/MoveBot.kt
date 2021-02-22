package io.nekohasekai.pm.manage.admin

import cn.hutool.core.util.NumberUtil
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.raw.getUserOrNull
import io.nekohasekai.ktlib.td.extensions.htmlInlineMention
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.database.UserBots
import io.nekohasekai.pm.manage.global
import td.TdApi

class MoveBot : AdminCommand() {

    override fun onLoad() {
        initFunction("_move_bot")
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

        if (params.size != 2 || !NumberUtil.isInteger(params[1])) {
            sudo make "/_move_bot <botId/username> <newOwnerId>" replyTo message
            return
        }

        val userBot = database {
            if (NumberUtil.isInteger(params[0])) {
                UserBot.find { UserBots.owner eq params[0].toInt() }.firstOrNull()
            } else {
                UserBot.find { UserBots.username eq params[0] }.firstOrNull()
            }
        }

        if (userBot == null) {
            sudo make "Bot ${params[0]} not found" replyTo message
            return
        }

        val newOwner = getUserOrNull(params[1].toInt())

        if (newOwner == null) {
            sudo make "User ${params[1]} not found" replyTo message
            return
        }

        try {
            sudo make "Bot @" + userBot.username + " moved to you" syncTo newOwner.id
        } catch (e: TdException) {
            sudo makeHtml "Can't access user " + newOwner.htmlInlineMention + " (${e.message})" replyTo message
            return
        }

        database.write {
            userBot.owner = newOwner.id
            userBot.flush()
        }

        global.instanceMap[userBot.botId]?.waitForClose()
        global.initBot(userBot)

        sudo make "Success" replyTo message

    }

}