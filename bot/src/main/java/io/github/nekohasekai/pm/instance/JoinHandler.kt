package io.github.nekohasekai.pm.instance

import cn.hutool.core.util.NumberUtil
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.raw.searchPublicChatOrNull
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.MessageRecord
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import td.TdApi

class JoinHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("join", "exit")

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        val integration = integration

        if (chatId != admin && chatId != integration?.integration) rejectFunction()

        if (chatId == integration?.integration) {

            if (integration.adminOnly && checkChatAdmin(message)) return

        }

        if (function == "join") {

            var chatToJoin = 0L

            if (message.replyToMessageId != 0L) {

                val record = database {

                    MessageRecord.find { MessageRecords.messageId eq message.replyToMessageId }.firstOrNull()

                }

                if (record == null) {

                    sudo make L.failed { RECORD_NF } to chatId send deleteDelay(message)

                    return

                }

                chatToJoin = record.chatId

            } else {

                for (entity in message.entities!!) {

                    if (entity.type is TdApi.TextEntityTypeMention) {

                        val username = message.text!!.substring(entity.offset, entity.offset + entity.length).substringAfter("@")

                        val user = searchPublicChatOrNull(username) ?: continue

                        chatToJoin = user.id

                        break

                    } else if (entity.type is TdApi.TextEntityTypeMentionName) {

                        chatToJoin = (entity.type as TdApi.TextEntityTypeMentionName).userId.toLong()

                        break

                    }

                }

            }

            if (chatToJoin == 0L && NumberUtil.isInteger(param)) {

                chatToJoin = param.toLong()

            }

            if (chatToJoin == 0L) {

                sudo make L.PM_HELP sendTo chatId

                return

            }

            val targetChat = try {

                getChat(chatToJoin)

            } catch (e: TdException) {

                sudo make L.failed { BANDED_BY } to chatId send deleteDelay(message)

                return

            }

            val chatType = targetChat.type

            if (chatType !is TdApi.ChatTypePrivate) {

                sudo make L.failed { JOIN_NON_PM } replyTo message

                return

            }

            val targetUser = try {

                getUser(chatType.userId)

            } catch (e: TdException) {

                sudo make L.failed { JOIN_NON_PM } replyTo message

                return

            }

            findHandler<OutputHandler>().currentChat = targetChat.id

            sudo makeHtml L.JOINED_NOTICE.input(targetUser.asIdMention, targetUser.displayNameHtml.asCode) sendTo chatId

        } else {

            val out = findHandler<OutputHandler>()

            if (out.currentChat != 0L) {

                out.currentChat = 0L

                sudo make L.EXITED sendTo chatId

            } else {

                sudo make L.NOTHING_TO_EXIT sendTo chatId

            }

        }

    }

}
