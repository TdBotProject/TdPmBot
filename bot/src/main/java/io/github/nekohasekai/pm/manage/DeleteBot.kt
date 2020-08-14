package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.FN_PRIVATE_ONLY
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances
import td.TdApi

class DeleteBot : UserBotSelector() {

    companion object {

        const val command = "delete_bot"

        val DEF = TdApi.BotCommand(
                command,
                LocaleController.DELETE_BOT_DEF
        )

    }

    override val persistId = PERSIST_DEL_BOT

    override fun onLoad() {

        super.onLoad()

        initFunction(command)

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (!Launcher.public && chatId != Launcher.admin) rejectFunction()

        if (!message.fromPrivate) {

            userCalled(userId, "delete in non-private chat")

            sudo make LocaleController.FN_PRIVATE_ONLY onSuccess deleteDelay(message) replyTo message

            return

        }

        val L = LocaleController.forChat(userId)

        if (!Launcher.public && chatId != Launcher.admin) {

            Launcher.sudo make L.PRIVATE_INSTANCE sendTo chatId

            return

        }

        userCalled(userId, "start select bot to delete")

        doSelect(L, userId, 0L, L.SELECT_TO_DELETE)

    }

    override suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?) {

        userBot!!

        userCalled(userId, "send delete confirm for bot @${userBot.username}")

        writePersist(userId, persistId, 1L, userBot.botId)

        val L = LocaleController.forChat(userId)

        sudo make L.DELETE_CONFIRM.input(userBot.username) sendTo chatId

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        if (subId == 1L) {

            val L = LocaleController.forChat(userId)

            val botUserId = data[0] as Int

            val userBot = database { UserBot.findById(botUserId) }

            if (userBot == null || userBot.owner != userId) {

                userCalled(userId, "outdated bot in persist")

                sudo make L.INVALID_SELECTED sendTo chatId

                return

            }

            if (message.text?.matches(L.DELETE_CONFIRM_REGEX.toRegex()) == true) {

                userCalled(userId, "confirmed delete bot @${userBot.username}")

                sudo removePersist userId

                sudo make Typing sendTo chatId

                val status = sudo make L.STOPPING syncTo chatId

                val bot = BotInstances.initBot(userBot)

                bot.waitForClose()

                sudo make Typing sendTo chatId

                sudo make L.DELETING editTo status

                bot.destroy()

                sudo make L.BOT_DELETED editTo status

            } else {

                sudo make L.CONFIRM_NOT_MATCH sendTo chatId

            }

        } else {

            super.onPersistMessage(userId, chatId, message, subId, data)

        }

    }

}