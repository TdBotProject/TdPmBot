package io.nekohasekai.pm.manage.admin

import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.utils.checkChatAdmin
import io.nekohasekai.pm.manage.global
import td.TdApi

abstract class AdminCommand : TdHandler() {

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>
    ) {
        val integration = global.integration
        if (userId.toLong() != global.admin &&
            (chatId != integration?.integration ||
                    (integration.adminOnly && !checkChatAdmin(message)))
        ) {
            rejectFunction()
        }
    }
}