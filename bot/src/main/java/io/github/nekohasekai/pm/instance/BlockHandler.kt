package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import cn.hutool.core.util.NumberUtil
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getChatMember
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.raw.searchPublicChatOrNull
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.NO_PERMISSION
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import td.TdApi

class BlockHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("block", "unblock", "ban", "unban")

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        val integration = integration

        if (chatId != admin && chatId != integration?.integration) rejectFunction()

        if (chatId == integration?.integration) {

            if (integration.adminOnly && checkChatAdmin(message)) return

        }

        var chatToBlock = 0L

        if (message.replyToMessageId != 0L) {

            val record = database {

                MessageRecords.select { currentBot and (MessageRecords.messageId eq message.replyToMessageId) }.firstOrNull()

            }

            if (record == null) {

                sudo make L.failed { RECORD_NF } onSuccess deleteDelay(message) sendTo chatId

                return

            }

            chatToBlock = record[MessageRecords.chatId]

        } else {

            for (entity in message.entities!!) {

                if (entity.type is TdApi.TextEntityTypeMention) {

                    val username = message.text!!.substring(entity.offset, entity.offset + entity.length).substringAfter("@")

                    val user = searchPublicChatOrNull(username) ?: continue

                    chatToBlock = user.id

                    break

                } else if (entity.type is TdApi.TextEntityTypeMentionName) {

                    chatToBlock = (entity.type as TdApi.TextEntityTypeMentionName).userId.toLong()

                    break

                }

            }

        }

        if (chatToBlock == 0L && NumberUtil.isInteger(param)) {

            chatToBlock = param.toLong()

        }

        if (chatToBlock == 0L) {

            sudo make L.PM_HELP sendTo chatId

            return

        }

        val userToBlock = chatToBlock.toInt()

        var userHtml: String

        try {

            val targetChat = getChat(chatToBlock)

            val chatType = targetChat.type

            if (chatType !is TdApi.ChatTypePrivate) throw TdException(L.USER_NOT_FOUND)

            userHtml = getUser(chatType.userId).asInlineMention

        } catch (e: TdException) {

            if (function in arrayOf("block", "ban")) {

                sudo make L.failed { USER_NOT_FOUND } replyTo message

                return

            }

            userHtml = "$chatToBlock"

        }

        val record = blocks.fetch(userToBlock).value == true

        fun saveNotice(noticeMessageId: Long) {

            database.write {

                MessageRecords.insert {

                    it[messageId] = noticeMessageId

                    it[type] = MESSAGE_TYPE_INPUT_NOTICE

                    it[this.chatId] = chatToBlock

                    it[createAt] = (SystemClock.now() / 100L).toInt()

                    it[botId] = me.id

                }

            }

        }

        if (function in arrayOf("block", "ban")) {

            if (record) {

                sudo makeHtml L.BLOCK_EXISTS.input(userHtml) syncReplyTo message

                return

            }

            if (chatId == integration?.integration) {

                if (integration.adminOnly && checkChatAdmin(message)) return

            }

            val userIdLong = userId.toLong()

            if (userToBlock == userId) {

                sudo makeHtml L.CANNOT_BLOCK_SELF syncReplyTo message

                return

            } else if (chatId == integration?.integration && userIdLong != admin) {

                val chatMember = getChatMember(chatId, userToBlock)

                if (chatMember.isAdmin || (chatMember.isMember && !integration.adminOnly)) {

                    // 阻止 接入群组成员屏蔽其他成员

                    sudo makeHtml L.NO_PERMISSION syncReplyTo message

                    return

                }

            }

            blocks.fetch(userToBlock).apply {

                value = true

                changed = true

                flush()

            }

            val notice = sudo makeHtml L.BLOCKED.input(userHtml) syncReplyTo message

            saveNotice(notice.id)

        } else {

            if (!record) {

                sudo makeHtml L.BLOCK_NOT_EXISTS.input(userHtml) syncReplyTo message

                return

            }

            blocks.fetch(userToBlock).apply {

                value = false

                changed = true

                flush()

            }

            val notice = sudo makeHtml L.UNBLOCKED.input(userHtml) syncReplyTo message

            saveNotice(notice.id)

        }

    }

}