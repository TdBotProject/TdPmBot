package io.nekohasekai.pm.manage

import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.core.utils.make
import io.nekohasekai.pm.Launcher
import td.TdApi

class AdminCommands : TdHandler() {

    companion object {

        const val COMMAND_GC = "gc"

    }

    override fun onLoad() {

        initFunction(COMMAND_GC)

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (userId != Launcher.admin.toInt()) rejectFunction()

        if (function == COMMAND_GC) {

            val status = sudo make "GC Executing..." syncTo chatId

            Launcher.gc()

            sudo make "GC Finished" editTo status

        }

    }

}