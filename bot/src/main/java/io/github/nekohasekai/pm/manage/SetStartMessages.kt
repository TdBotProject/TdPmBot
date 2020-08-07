package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.utils.asInputOrForward
import io.github.nekohasekai.nekolib.core.utils.input
import io.github.nekohasekai.nekolib.core.utils.invoke
import io.github.nekohasekai.nekolib.core.utils.make
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.nekolib.i18n.SETTING_SAVED
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.StartMessages
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances
import td.TdApi
import java.util.*

class SetStartMessages : UserBotSelector(true) {

    val dataId = DATA_SET_START_MESSAGES

    override val persistId = PERSIST_SET_START_MESSAGES

    val function = "set_start_messages"

    val DEF = TdApi.BotCommand(
            function,
            LocaleController.SET_STARTS_DEF
    )

    override fun onLoad() {

        super.onLoad()

        if (sudo is Launcher) {

            initFunction(function)

            initData(dataId)

        }

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        val L = LocaleController.forChat(userId)

        doSelect(userId, 0L, L.SELECT_TO_SET)

    }

    class StartMessagesCache(

            val botId: Int,
            val messages: LinkedList<TdApi.InputMessageContent> = LinkedList()

    )

    override suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?) {

        val L = LocaleController.forChat(userId)

        if (userBot == null) {

            startSet(L, userId, chatId)

            return

        }

        BotInstances.initBot(userBot).findHandler<SetStartMessages>().startSet(L, userId, chatId)

        sudo make L.JUMP_TO_SET.input(userBot.username) removeKeyboard true sendTo chatId

    }

    suspend fun startSet(L: LocaleController, userId: Int, chatId: Long) {

        val cache = StartMessagesCache(me.id)

        writePersist(userId, persistId, 1L, cache, allowFunction = true)

        sudo make L.INPUT_MESSAGES removeKeyboard true syncTo chatId

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        super.onPersistMessage(userId, chatId, message, subId, data)

        if (subId == 1L) {

            val L = LocaleController.forChat(userId)

            val cache = data[0] as StartMessagesCache

            val content = message.asInputOrForward

            cache.messages.add(content)

            if (content is TdApi.InputMessageForwarded) {

                sudo make L.MESSAGE_ADDED_FWD sendTo chatId

            } else {

                sudo make L.MESSAGE_ADDED sendTo chatId

            }

        }

    }

    override suspend fun onPersistFunction(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (function != "submit") rejectFunction()

        sudo removePersist userId

        val L = LocaleController.forChat(userId)

        val cache = data[0] as StartMessagesCache

        val botUserId = cache.botId

        if (!Launcher.admins.contains(userId) && database { UserBot.findById(botUserId)?.owner != userId }) {

            // 权限检查

            sudo make L.INVALID_SELECTED sendTo chatId

            return

        }

        val column = StartMessages.Cache.fetch(botUserId)

        column.value = cache.messages
        column.changed = true

        StartMessages.Cache.remove(botUserId)

        sudo make L.SETTING_SAVED sendTo chatId

    }

}