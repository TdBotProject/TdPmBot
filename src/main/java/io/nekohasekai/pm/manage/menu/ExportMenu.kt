package io.nekohasekai.pm.manage.menu

import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.manage.BotHandler

class ExportMenu : BotHandler() {

    override suspend fun onNewBotCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>,
        botUserId: Int,
        userBot: UserBot?
    ) {

    }
}