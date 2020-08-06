package io.github.nekohasekai.pm.manage

import cn.hutool.core.io.FileUtil
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.database.UserBots
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import td.TdApi

class DeleteBot : TdHandler() {

    val persistId = PERSIST_DEL_BOT

    override fun onLoad() {

        initFunction("delete_bot")

        initPersist(persistId)

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        val bots = database { UserBot.find { UserBots.owner eq userId }.toList() }

        if (bots.isEmpty()) {

            sudo make "You don't have any bots yet. Use the /new_bot command to create a new bot first." sendTo chatId

            return

        }

        writePersist(userId, persistId, 0L)

        sudo make "Choose a bot to delete." withMarkup keyboadButton {

            var line: KeyboadButtonBuilder.Line? = null

            bots.forEach {

                line = if (line == null) {

                    newLine {

                        textLine("@${it.username}")

                    }

                } else {

                    line!!.textButton("@${it.username}")

                    null

                }

            }

        } sendTo chatId

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<ByteArray>) {

        fun invalidBot() {

            sudo make "Invalid bot selected." withMarkup TdApi.ReplyMarkupShowKeyboard() sendTo chatId

        }

        if (subId == 0L) {

            var botUserName = message.text

            if (botUserName == null || !botUserName.startsWith("@")) {

                invalidBot()

                return

            }

            botUserName = botUserName.substring(1)

            val userBot = database {

                UserBot.find { (UserBots.username eq botUserName) and (UserBots.owner eq userId) }.firstOrNull()

            }

            if (userBot == null) {

                invalidBot()

                return

            }

            writePersist(userId, persistId, 1L, userBot.botId.toByteArray())

            sudo make "OK, you selected @${userBot.username}. Are you sure?\n" +
                    "\n" +
                    "Send 'Yes, I am totally sure.' to confirm you really want to delete this bot." removeKeyboard true sendTo chatId

        } else if (subId == 1L) {

            val botUserId = data[0].toInt()

            val userBot = database { UserBot.findById(botUserId) }

            if (userBot == null || userBot.owner != userId) {

                invalidBot()

                return

            }

            if ("Yes, I am totally sure." == message.text) {

                sudo removePersist userId

                val status = sudo make "Stopping..." syncTo chatId

                val bot = BotInstances.initBot(userBot).apply {

                    waitForClose()

                }

                sudo make "Clearing database..." editTo status

                FileUtil.del(bot.options.databaseDirectory)

                database {

                    bot.messageRecords.dropStatement()
                    UserBot.removeFromCache(userBot)
                    UserBots.deleteWhere { UserBots.botId eq botUserId }

                }

                BotInstances.instanceMap.remove(botUserId)

                sudo make "Done! The bot is gone." editTo status

            } else {

                sudo make "Please enter the confirmation text exactly like this:\n" +
                        "Yes, I am totally sure.\n" +
                        "\n" +
                        "Type /cancel to cancel the operation." sendTo chatId

            }

        }

    }

}