package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.JUMP_TO_SET
import io.github.nekohasekai.pm.Launcher
import io.github.nekohasekai.pm.PERSIST_SET_START_INTEGRATION
import io.github.nekohasekai.pm.database.UserBot
import td.TdApi

class SetIntegration : UserBotSelector(true) {

    override val persistId = PERSIST_SET_START_INTEGRATION

    override fun onLoad() {

        if (sudo is Launcher) {

            initFunction("set_integration")

        }

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        val L = LocaleController.forChat(userId)

        doSelect(L, userId, 0L, L.JUMP_TO_SET)

    }

    override suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?) {



    }

}