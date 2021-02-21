package io.nekohasekai.pm.manage.admin

import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.pm.launcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import td.TdApi

class Gc : AdminCommand() {

    override fun onLoad() {
        initFunction("_gc")
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

        val status = sudo make "GC Executing..." syncTo chatId

        withContext(Dispatchers.IO) {
            launcher.gc()
            sudo make "GC Finished" editTo status
        }
    }

}