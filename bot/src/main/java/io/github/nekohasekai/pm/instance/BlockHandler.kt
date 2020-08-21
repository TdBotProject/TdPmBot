package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getChatMember
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.NO_PERMISSION
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.insert
import td.TdApi

class BlockHandler(pmInstance: PmInstance) : AbstractUserInputHandler(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("block", "unblock", "ban", "unban")

    }

    override suspend fun gc() {

        blocks.clear()

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        val integration = integration

        if (chatId != admin && chatId != integration?.integration) rejectFunction()

        if (chatId == integration?.integration) {

            if (integration.adminOnly && checkChatAdmin(message)) return

        }

        super.onFunction(userId, chatId, message, function, param, params, originParams)

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, targetUser: Int) {

        if (targetUser == 0) {

            sudo make L.PM_HELP sendTo chatId

            return

        }

        var userHtml: String

        try {

            val targetChat = getChat(targetUser.toLong())

            val chatType = targetChat.type

            if (chatType !is TdApi.ChatTypePrivate) throw TdException(L.USER_NOT_FOUND)

            userHtml = getUser(chatType.userId).asInlineMention

        } catch (e: TdException) {

            if (function in arrayOf("block", "ban")) {

                sudo make L.failed { USER_NOT_FOUND } replyTo message

                return

            }

            userHtml = "$targetUser"

        }

        val record = blocks.fetch(targetUser).value == true

        fun saveNotice(noticeMessageId: Long) {

            database.write {

                MessageRecords.insert {

                    it[messageId] = noticeMessageId

                    it[type] = MESSAGE_TYPE_INPUT_NOTICE

                    it[this.chatId] = targetUser.toLong()

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

            if (targetUser == userId) {

                sudo makeHtml L.CANNOT_BLOCK_SELF syncReplyTo message

                return

            } else if (chatId == integration?.integration && userIdLong != admin) {

                val chatMember = getChatMember(chatId, targetUser)

                if (chatMember.isAdmin || (chatMember.isMember && !integration.adminOnly)) {

                    // 阻止 接入群组成员屏蔽其他成员

                    sudo makeHtml L.NO_PERMISSION syncReplyTo message

                    return

                }

            }

            blocks.fetch(targetUser).apply {

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

            blocks.fetch(targetUser).apply {

                value = false

                changed = true

                flush()

            }

            val notice = sudo makeHtml L.UNBLOCKED.input(userHtml) syncReplyTo message

            saveNotice(notice.id)

        }

    }

}