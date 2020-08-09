package io.github.nekohasekai.pm.manage

import cn.hutool.core.io.FileUtil
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.database.UserBots
import io.github.nekohasekai.pm.instance.BotInstances
import org.jetbrains.exposed.sql.deleteWhere
import td.TdApi

class DeleteBot : UserBotSelector() {

    val command = "delete_bot"

    val DEF = TdApi.BotCommand(
            command,
            LocaleController.DELETE_BOT_DEF
    )

    override val persistId = PERSIST_DEL_BOT

    override fun onLoad() {

        super.onLoad()

        initFunction(command)

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        userCalled(userId, "start select bot to delete")

        val L = LocaleController.forChat(userId)

        doSelect(userId, 0L , L.SELECT_TO_DELETE)

    }

    override suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?) {

        userBot!!

        userCalled(userId, "send delete confirm for bot @${userBot.username}")

        writePersist(userId, persistId, 1L, userBot.botId.toByteArray())

        val L = LocaleController.forChat(userId)

        sudo make L.DELETE_CONFIRM.input(userBot.username) removeKeyboard true sendTo chatId

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        if (subId == 1L) {

            val L = LocaleController.forChat(userId)

            val botUserId = (data[0] as ByteArray).toInt()

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

                val bot = BotInstances.initBot(userBot).apply {

                    waitForClose()

                }

                sudo make Typing sendTo chatId

                sudo make L.DELETING editTo status

                FileUtil.del(bot.options.databaseDirectory)

                database {

                    bot.messageRecords.dropStatement()
                    UserBot.removeFromCache(userBot)
                    UserBots.deleteWhere { UserBots.botId eq botUserId }

                }

                BotInstances.instanceMap.remove(botUserId)

                sudo make CancelChatAction sendTo chatId

                sudo make L.BOT_DELETED editTo status

            } else {

                sudo make L.CONFIRM_NOT_MATCH sendTo chatId

            }

        } else {

            super.onPersistMessage(userId, chatId, message, subId, data)

        }

    }

}