package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getChatMemberOrNull
import io.github.nekohasekai.nekolib.core.raw.getMessage
import io.github.nekohasekai.nekolib.core.raw.getMessageOrNull
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import td.TdApi

class OutputHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    var currentChat = 0L

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        val integration = integration

        val useIntegration = chatId == integration?.integration

        if (chatId != admin && !useIntegration) return

        fun saveSent(targetChat: Long, sentMessageId: Long) {

            database.write {

                MessageRecords.insert {

                    it[messageId] = message.id

                    it[type] = MESSAGE_TYPE_OUTPUT_MESSAGE

                    it[this.chatId] = targetChat

                    it[targetId] = sentMessageId

                    it[createAt] = (SystemClock.now() / 100L).toInt()

                    it[botId] = me.id

                }

                MessageRecords.insert {

                    it[messageId] = sentMessageId

                    it[type] = MESSAGE_TYPE_OUTPUT_FORWARDED

                    it[this.chatId] = targetChat

                    it[targetId] = message.id

                    it[createAt] = (SystemClock.now() / 100L).toInt()

                    it[botId] = me.id

                }

            }

        }

        if (message.replyToMessageId == 0L) {

            if (currentChat == 0L) {

                if (sudo is PmBot && chatId == admin) {

                    sudo make L.PM_HELP syncReplyTo message

                }

                return

            }

            if (useIntegration && integration!!.adminOnly && checkChatAdmin(message)) return

            val sentMessage = try {

                sudo make message.asInputOrForward syncTo currentChat

            } catch (e: TdException) {

                sudo make L.failed { e.message } replyTo message send deleteDelay(message)

                return

            }

            saveSent(currentChat, sentMessage.id)

            sudo make L.SENT replyTo message send deleteDelayIf(!useIntegration)

            return

        }

        if (useIntegration && integration!!.adminOnly && checkChatAdmin(message)) return

        val record = database {

            MessageRecords.select { currentBot and (MessageRecords.messageId eq message.replyToMessageId) }.firstOrNull()

        }

        if (record == null) {

            if (useIntegration && getMessageOrNull(chatId, message.replyToMessageId)?.let { getChatMemberOrNull(chatId, it.senderUserId)?.status?.isMember } == true) {

                // 如果回复的用户在群组里即跳过找不到记录提示

                return

            }

            sudo make L.RECORD_NF replyTo message send deleteDelay(message)

            return

        }

        suspend fun getTargetChat() = try {

            getChat(record[MessageRecords.chatId])

        } catch (e: TdException) {

            sudo make L.failed { USER_NOT_FOUND } replyTo message send deleteDelayIf(!useIntegration, message)

            null

        }

        when (record[MessageRecords.type]) {

            MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE -> {

                defaultLog.warn("Please delete the data after changing the bot admin.")

            }

            MessageRecords.MESSAGE_TYPE_INPUT_NOTICE -> {

                val targetUser = getTargetChat() ?: return

                val sentMessage = try {

                    sudo make message.content.asInput!! syncTo targetUser.id

                } catch (e: TdException) {

                    sudo make L.failed { e.message } replyTo message send deleteDelayIf(!useIntegration, message)

                    return

                }

                saveSent(targetUser.id, sentMessage.id)

                sudo make L.SENT replyTo message send deleteDelayIf(!useIntegration)

            }

            MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
            MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                val targetUser = getTargetChat() ?: return

                val targetMessage = try {

                    getMessage(targetUser.id, record[MessageRecords.targetId]!!)

                } catch (e: TdException) {

                    sudo make L.failed { REPLIED_NF } replyTo message send deleteDelayIf(!useIntegration, message)

                    return

                }

                val sentMessage = try {

                    sudo make message.content.asInput!! syncReplyTo targetMessage

                } catch (e: TdException) {

                    sudo make L.failed { e.message } replyTo message send deleteDelayIf(!useIntegration, message)

                    return

                }

                saveSent(targetUser.id, sentMessage.id)

                sudo make L.REPLIED replyTo message send deleteDelayIf(!useIntegration)

            }

        }

    }

}
