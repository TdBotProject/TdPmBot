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
import io.github.nekohasekai.pm.database.PmInstance
import td.TdApi

class JoinHandler(private val admin: Int, pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("join", "exit")

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (chatId != admin.toLong()) rejectFunction()

        if (function == "join") {

            var chatToJoin = 0L

            if (message.replyToMessageId != 0L) {

                val record = database {

                    messages.find { messageRecords.messageId eq message.replyToMessageId }.firstOrNull()

                }

                if (record == null) {

                    sudo make L.failed { RECORD_NF } to chatId send deleteDelay(message)

                    return

                }

                chatToJoin = record.chatId

            } else {

                for (entity in message.entities!!) {

                    if (entity.type is TdApi.TextEntityTypeMention) {

                        val username = message.text!!.substring(entity.offset, entity.length).substringAfter("@")

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

            if (!chatId.fromPrivate) {

                sudo make L.failed { JOIN_NON_PM } to chatId send deleteDelay(message)

                return

            }

            val targetChat = try {

                getChat(chatToJoin)

            } catch (e: TdException) {

                sudo make L.failed { BANDED_BY } to chatId send deleteDelay(message)

                return

            }

            findHandler<OutputHandler>().currentChat = targetChat.id

            val user = getUser(targetChat.id.toInt())

            sudo makeHtml L.JOINED_NOTICE.input(user.asIdMention, user.displayNameHtml.asCode) sendTo chatId

        } else {

            findHandler<OutputHandler>().currentChat = 0L

            sudo make L.EXITED sendTo chatId

        }

    }

}