package io.nekohasekai.pm.manage.admin

import td.TdApi

class DeleteBots : AdminCommand() {

    override fun onLoad() {
        initFunction(
            "_delete_bot",
            "_delete_by_user"
        )
    }

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>
    ) {

    }

}