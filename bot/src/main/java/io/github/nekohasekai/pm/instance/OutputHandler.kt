package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getChatMember
import io.github.nekohasekai.nekolib.core.raw.getMessage
import io.github.nekohasekai.nekolib.core.raw.getMessageOrNull
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.MessageRecord
import io.github.nekohasekai.pm.database.PmInstance
import td.TdApi

class OutputHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    var currentChat = 0L

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        val integration = integration

        val useIntegration = chatId == integration?.integration

        if (chatId != admin && !useIntegration) return

        fun saveSent(targetChat: Long, sentMessageId: Long) {

            database.write {

                messages.new(message.id) {

                    type = MessageRecord.MESSAGE_TYPE_OUTPUT_MESSAGE

                    this.chatId = targetChat

                    targetId = sentMessageId

                    createAt = (SystemClock.now() / 100L).toInt()

                }

                messages.new(sentMessageId) {

                    type = MessageRecord.MESSAGE_TYPE_OUTPUT_FORWARDED

                    this.chatId = targetChat

                    targetId = message.id

                    createAt = (SystemClock.now() / 100L).toInt()

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

            messages.find { (messageRecords.messageId eq message.replyToMessageId) }.firstOrNull()

        }

        if (record == null) {

            if (useIntegration) {

                val repliedMessage = getMessageOrNull(chatId, message.replyToMessageId)

                if (repliedMessage != null) {

                    if (getChatMember(chatId, repliedMessage.senderUserId).status.isMember) {

                        // 如果回复的用户在群组里即跳过找不到记录提示

                        return

                    }

                }

            }

            sudo make L.RECORD_NF replyTo message send deleteDelay(message)

            return

        }

        suspend fun getTargetChat() = try {

            getChat(record.chatId)

        } catch (e: TdException) {

            sudo make L.failed { BANDED_BY } replyTo message send deleteDelayIf(!useIntegration, message)

            null

        }

        when (record.type) {

            MessageRecord.MESSAGE_TYPE_INPUT_MESSAGE -> {

                defaultLog.warn("Please delete the data after changing the bot admin.")

            }

            MessageRecord.MESSAGE_TYPE_INPUT_NOTICE -> {

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

            MessageRecord.MESSAGE_TYPE_INPUT_FORWARDED,
            MessageRecord.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                val targetUser = getTargetChat() ?: return

                val targetMessage = try {

                    getMessage(targetUser.id, record.targetId!!)

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
