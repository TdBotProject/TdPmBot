package io.nekohasekai.pm.manage.admin

import io.nekohasekai.pm.manage.global
import td.TdApi
import java.io.File

class RevokeAll : AdminCommand() {

    override fun onLoad() {
        initFunction("_revoke_all")
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

        val instances = global.instanceMap.values.toList()

        for (bot in instances) bot.stop()
        for (bot in instances) bot.waitForClose()
        for (bot in instances) File(bot.options.databaseDirectory).listFiles()?.forEach {
            if (it.isFile && (it.extension == "binlog" || it.startsWith("db."))) it.delete()
        }
        for (bot in instances) global.initBot(bot.userBot)
    }

}