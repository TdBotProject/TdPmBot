package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.BOT_SELECTED
import io.github.nekohasekai.pm.INVALID_SELECTED
import io.github.nekohasekai.pm.Launcher
import io.github.nekohasekai.pm.NO_BOTS
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.database.UserBots
import org.jetbrains.exposed.sql.and
import td.TdApi

abstract class UserBotSelector(val allowSelf: Boolean = false) : TdHandler() {

    abstract val persistId: Int

    override fun onLoad() {

        initPersist(persistId)

    }

    suspend fun doSelect(L: LocaleController, userId: Int, subId: Long, message: String) {

        var bots = database { UserBot.find { UserBots.owner eq userId }.toList() }.map { it.username }

        if (allowSelf && userId == Launcher.admin.toInt()) {

            bots = listOf(Launcher.me.username, * bots.toTypedArray())

        }

        if (bots.isEmpty()) {

            userCalled(userId, "no any bots yet")

            sudo make L.NO_BOTS sendTo userId

            return

        }

        userCalled(userId, "bots: ${bots.joinToString(", ") { "@$it" }}")

        writePersist(userId, persistId, subId)

        sudo make message withMarkup keyboardButton {

            var line: KeyboardButtonBuilder.Line? = null

            bots.forEach {

                if (line == null) {

                    line = newLine()
                    line!!.textButton("@$it")

                } else {

                    line!!.textButton("@$it")
                    line = null

                }


            }

        } sendTo userId

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any>) {

        if (subId == 0L) {

            val L = LocaleController.forChat(userId)

            fun invalidBot() {

                sudo make L.INVALID_SELECTED sendTo chatId

            }

            var botUserName = message.text

            if (botUserName == null || !botUserName.startsWith("@")) {

                userCalled(userId, "not a bot username")

                invalidBot()

                return

            }

            botUserName = botUserName.substring(1)

            if (chatId == Launcher.admin && botUserName == me.username) {

                sudo removePersist userId

                delayDelete(
                        sudo makeHtml L.BOT_SELECTED.input(me.username) withMarkup removeKeyboard() syncTo chatId
                )

                onSelected(userId, chatId, subId, null)

                return

            }

            val userBot = database {

                UserBot.find { (UserBots.username eq botUserName) and (UserBots.owner eq userId) }.firstOrNull()

            }

            if (userBot == null) {

                userCalled(userId, "bot record not found or has diff owner")

                invalidBot()

                return

            }

            sudo removePersist userId

            delayDelete(
                    sudo makeHtml L.BOT_SELECTED.input(userBot.username) withMarkup removeKeyboard() syncTo chatId
            )

            onSelected(userId, chatId, subId, userBot)

        }

    }

    abstract suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?)

}