package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.INVALID_SELECTED
import io.github.nekohasekai.pm.Launcher
import io.github.nekohasekai.pm.NO_BOTS
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.database.UserBots
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import td.TdApi

abstract class UserBotSelector(val allowSelf: Boolean = false) : TdHandler() {

    abstract val persistId: Int

    override fun onLoad() {

        initPersist(persistId)

    }

    fun doSelect(userId: Int, subId: Long,message: String) {

        val L = LocaleController.forChat(userId)

        var bots = database { UserBot.find { UserBots.owner eq userId }.toList() }.map { it.username }

        if (allowSelf && Launcher.admins.contains(userId)) {

            bots = listOf(Launcher.me.username, * bots.toTypedArray())

        }

        if (bots.isEmpty()) {

            sudo make L.NO_BOTS sendTo userId

            return

        }

        writePersist(userId, persistId, 0L, subId.toByteArray())

        sudo make message withMarkup keyboadButton {

            var line: KeyboadButtonBuilder.Line? = null

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

                invalidBot()

                return

            }

            botUserName = botUserName.substring(1)

            if (Launcher.admins.contains(userId) && botUserName == Launcher.me.username) {

                sudo removePersist userId

                onSelected(userId, chatId, (data[0] as ByteArray).toLong(), null)

                return

            }

            val userBot = database {

                UserBot.find { (UserBots.username eq botUserName) and (UserBots.owner eq userId) }.firstOrNull()

            }

            if (userBot == null) {

                invalidBot()

                return

            }

            sudo removePersist userId

            onSelected(userId, chatId, (data[0] as ByteArray).toLong(), userBot)

        }

    }

    abstract suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?)

}