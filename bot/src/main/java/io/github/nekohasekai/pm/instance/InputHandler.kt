package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import io.github.nekohasekai.pm.manage.menu.IntegrationMenu
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import td.TdApi
import java.util.concurrent.atomic.AtomicInteger

class InputHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    private var currentUser by AtomicInteger()
    private var times by AtomicInteger()

    override fun onLoad() {

        super.onLoad()

        initPersist(PERSIST_UNDER_FUNCTION)

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any?>) {

        sudo removePersist userId

        onNewMessage(userId, chatId, message, data[0] as String)

    }

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) = onNewMessage(userId, chatId, message, null)

    suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message, command: String?) {

        val integration = integration

        if (admin == chatId || chatId == integration?.integration || !message.fromPrivate) return

        userCalled(userId, "收到消息: ${message.text ?: "<${message.content.javaClass.simpleName.substringAfter("Message")}>"}")

        suspend fun MessageFactory.syncToTarget(): TdApi.Message? {

            if (integration != null && !integration.paused) {

                try {

                    getChat(integration.integration)

                    return this syncTo integration.integration

                } catch (e: TdException) {

                    defaultLog.warn(e)

                    database.write {

                        integration.paused = true
                        integration.flush()

                    }

                    Launcher make L.INTEGRATION_PAUSED_NOTICE.input(me.username) syncTo admin

                    Launcher.findHandler<IntegrationMenu>().integrationMenu(L, me.id, null, admin.toInt(), admin, 0L, false)

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

            currentUser = userId
            times = 5

            val user = getUser(userId)

            val inputNotice = if (command != null) {

                sudo makeHtml L.INPUT_FN_NOTICE.input(user.id, user.asInlineMention, command)

            } else {

                sudo makeHtml L.INPUT_NOTICE.input(user.id, user.asInlineMention)

            }.syncToTarget() ?: return

            database.write {

                MessageRecords.insert {

                    it[messageId] = inputNotice.id

                    it[type] = MESSAGE_TYPE_INPUT_NOTICE

                    it[this.chatId] = chatId

                    it[createAt] = (SystemClock.now() / 100L).toInt()

                    it[botId] = me.id

                }

            }

        } else {

            times--

        }

        val forwardedMessage = (sudo make inputForward(message) {

            if (settings?.twoWaySync == true) {

                copyOptions.sendCopy = true

            }

        }).apply {

            if (settings?.twoWaySync == true && message.replyToMessageId != 0L) {

                val record = database {

                    MessageRecords.select {

                        messagesForCurrentBot and (MessageRecords.messageId eq message.replyToMessageId) and MessageRecords.targetId.isNotNull()

                    }.firstOrNull()

                }

                if (record != null) {

                    replyToMessageId = record[MessageRecords.targetId]!!

                }


            }

        }.syncToTarget() ?: return

        database.write {

            MessageRecords.insert {

                it[messageId] = message.id

                it[type] = MESSAGE_TYPE_INPUT_MESSAGE

                it[this.chatId] = chatId

                it[targetId] = forwardedMessage.id

                it[createAt] = (SystemClock.now() / 100L).toInt()

                it[botId] = me.id

            }

        }

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