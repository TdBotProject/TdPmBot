package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.pm.INPUT_NOTICE
import io.github.nekohasekai.pm.INTEGRATION_PAUSED_NOTICE
import io.github.nekohasekai.pm.Launcher
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import io.github.nekohasekai.pm.manage.SetIntegration
import org.jetbrains.exposed.sql.insert
import td.TdApi
import java.util.concurrent.atomic.AtomicInteger

class InputHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    private var currentUser by AtomicInteger()
    private var times by AtomicInteger()

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        val integration = integration

        if (admin == chatId || chatId == integration?.integration || !message.fromPrivate) return

        database.write {

            MessageRecords.insert {

                it[messageId] = message.id

                it[type] = MESSAGE_TYPE_INPUT_MESSAGE

                it[this.chatId] = chatId

                it[createAt] = (SystemClock.now() / 100L).toInt()

                it[botId] = me.id

            }

        }

        suspend fun MessageFactory.syncToTarget(): TdApi.Message? {

            if (integration != null && !integration.paused) {

                try {

                    return this syncTo integration.integration

                } catch (e: TdException) {

                    defaultLog.warn(e)

                    database.write {

                        integration.paused = true
                        integration.flush()

                    }

                    Launcher make L.INTEGRATION_PAUSED_NOTICE syncTo admin

                    Launcher.findHandler<SetIntegration>().startSet(L, me.id, me.username, admin.toInt(), admin, 0L, true)

                }

            }

            try {

                getChat(admin)

                return this syncTo admin

            } catch (e: TdException) {

                defaultLog.warn(e, "banned by owner")

                // TODO: PROCESS BOT BANNED BY OWNER

            }

            return null

        }

        if (userId != currentUser || times < 1) {

            val user = getUser(userId)

            val inputNotice = (sudo makeHtml L.INPUT_NOTICE.input(user.asIdMention, user.displayNameHtml)).syncToTarget()
                    ?: return

            database.write {

                MessageRecords.insert {

                    it[messageId] = inputNotice.id

                    it[type] = MESSAGE_TYPE_INPUT_NOTICE

                    it[this.chatId] = chatId

                    it[createAt] = (SystemClock.now() / 100L).toInt()

                    it[botId] = me.id

                }

            }

            currentUser = userId
            times = 5

        } else {

            times--

        }

        val forwardedMessage = (sudo makeForward message).syncToTarget() ?: return

        database.write {

            MessageRecords.insert {

                it[messageId] = forwardedMessage.id

                it[type] = MESSAGE_TYPE_INPUT_FORWARDED

                it[this.chatId] = chatId

                it[targetId] = message.id

                it[createAt] = (SystemClock.now() / 100L).toInt()

                it[botId] = me.id

            }

        }

        finishEvent()

    }

}