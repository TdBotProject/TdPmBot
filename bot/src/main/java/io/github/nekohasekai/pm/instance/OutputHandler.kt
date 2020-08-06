package io.github.nekohasekai.pm.instance

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

    var currentChat = 0L

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        if (chatId != userId.toLong() || userId != admin) return

        fun saveSended(targetChat: Long, sendedMessageId: Long) {

            database {

                messages.new(message.id) {

                    type = MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE

                    this.chatId = targetChat

                    targetId = sendedMessageId

                    createAt = (SystemClock.now() / 100L).toInt()

                }

                messages.new(sendedMessageId) {

                    type = MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED

                    this.chatId = targetChat

                    targetId = message.id

                    createAt = (SystemClock.now() / 100L).toInt()

                }

            }

        }

        if (message.replyToMessageId == 0L) {

            if (currentChat == 0L) {

                findHandler(JoinHandler::class).joinHelp(chatId)

                return

            }

            val sendedMessage = try {

                sudo make message.content.asInput!! syncTo currentChat

            } catch (e: TdException) {

                sudo make "Failed: ${e.message}" replyTo message send deleteDelay(message)

                return

            }

            saveSended(currentChat, sendedMessage.id)

            sudo make "Sended." replyTo message send deleteDelay()

            return

        }

        val record = database {

            messages.find { messageRecords.messageId eq message.replyToMessageId }.firstOrNull()

        }

        if (record == null) {

            sudo make "Record not found." replyTo message send deleteDelay(message)

            return

        }

        suspend fun getTargetChat() = try {

            getChat(record.chatId)

        } catch (e: TdException) {

            sudo make "Failed: banned by user." replyTo message send deleteDelay(message)

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

                    sudo make "Failed: ${e.message}" replyTo message send deleteDelay(message)

                    return

                }

                saveSended(targetUser.id, sendedMessage.id)

                sudo make "Sended." replyTo message send deleteDelay()

            }

            MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
            MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                val targetUser = getTargetChat() ?: return

                val targetMessage = try {

                    getMessage(targetUser.id, record.targetId!!)

                } catch (e: TdException) {

                    sudo make "Replied message not found: ${e.message}" replyTo message send deleteDelay(message)

                    return

                }

                val sendedMessage = try {

                    sudo make message.content.asInput!! syncReplyTo targetMessage

                } catch (e: TdException) {

                    sudo make "Failed: ${e.message}" replyTo message send deleteDelay(message)

                    return

                }

                saveSended(targetUser.id, sendedMessage.id)

                sudo make "Replied." replyTo message send deleteDelay()

            }

        }

    }

}
