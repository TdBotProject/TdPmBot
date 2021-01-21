package io.nekohasekai.pm.manage

import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.pm.launcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import td.TdApi
import java.io.File

class AdminCommands : TdHandler() {

    companion object {

        const val COMMAND_GC = "gc"
        const val REVOKE_ALL = "revoke_all"

    }

    override fun onLoad() {

        initFunction(COMMAND_GC, REVOKE_ALL, "error")

    }

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>
    ) {

        if (userId != launcher.admin.toInt()) rejectFunction()

        if (function == COMMAND_GC) {

            val status = sudo make "GC Executing..." syncTo chatId

            withContext(Dispatchers.IO) {

                launcher.gc()

                sudo make "GC Finished" editTo status

            }

        } else if (function == REVOKE_ALL) {

            val instances = global.instanceMap.values.toList()

            for (bot in instances) bot.stop()
            for (bot in instances) bot.waitForClose()
            for (bot in instances) File(bot.options.databaseDirectory).listFiles()?.forEach {
                if (it.isFile && (it.extension == "binlog" || it.startsWith("db."))) it.delete()
            }
            for (bot in instances) global.initBot(bot.userBot)

        } else error("Error!")

    }

}