package io.github.nekohasekai.pm

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getMessage
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import td.TdApi

class OutputHandler(private val admin: Int, pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        if (userId != admin || !message.fromPrivate) return

        if (message.replyToMessageId == 0L) {

            sudo make "Use /join <chatId/username> to join a chat" to chatId send deleteDelay(message)

            return

        }

        val record = database {

            messages.find { messageRecords.messageId eq message.replyToMessageId }.firstOrNull()

        }

        if (record == null) {

            sudo make "Record not found." to chatId send deleteDelay(message)

            return

        }

        fun saveSended(targetChat: Long, sendedMessageId: Long) {

            database {

                messages.new {

                    messageId = message.id

                    type = MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE

                    this.chatId = targetChat

                    targetId = sendedMessageId

                    createAt = (SystemClock.now() / 100L).toInt()

                }

                messages.new {

                    messageId = sendedMessageId

                    type = MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED

                    this.chatId = targetChat

                    targetId = message.id

                    createAt = (SystemClock.now() / 100L).toInt()

                }

            }

        }

        suspend fun getTargetChat() = try {

            getChat(record.chatId)

        } catch (e: TdException) {

            sudo make "Failed: banned by user." to chatId send deleteDelay(message)

            null

        }

        when (record.type) {

            MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE -> {

                defaultLog.warn("Please delete the data after changing the bot admin.")

            }

            MessageRecords.MESSAGE_TYPE_INPUT_NOTICE -> {

                val targetUser = getTargetChat() ?: return

                val sendedMessage = try {

                    sudo make message.content.asInput!! syncTo targetUser.id

                } catch (e: TdException) {

                    sudo make "Failed: ${e.message}" to chatId send deleteDelay(message)

                    return

                }

                saveSended(targetUser.id, sendedMessage.id)

                sudo make "Sended." to chatId send deleteDelay()

            }

            MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
            MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                val targetUser = getTargetChat() ?: return

                val targetMessage = try {

                    getMessage(targetUser.id, record.targetId!!)

                } catch (e: TdException) {

                    sudo make "Replied message not found: ${e.message}" to chatId send deleteDelay(message)

                    return

                }

                val sendedMessage = try {

                    sudo make message.content.asInput!! syncReplyTo targetMessage

                } catch (e: TdException) {

                    sudo make "Failed: ${e.message}" to chatId send deleteDelay(message)

                    return

                }

                saveSended(targetUser.id, sendedMessage.id)

                sudo make "Replied." to chatId send deleteDelay()

            }

        }

    }

}
