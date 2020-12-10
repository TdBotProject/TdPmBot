package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.raw.getChat
import io.nekohasekai.ktlib.td.core.raw.getChatMember
import io.nekohasekai.ktlib.td.core.raw.getUser
import io.nekohasekai.ktlib.td.extensions.htmlInlineMention
import io.nekohasekai.ktlib.td.extensions.isAdmin
import io.nekohasekai.ktlib.td.extensions.isMember
import io.nekohasekai.ktlib.td.i18n.NO_PERMISSION
import io.nekohasekai.ktlib.td.i18n.failed
import io.nekohasekai.ktlib.td.utils.checkChatAdmin
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.L
import io.nekohasekai.pm.database.MessageRecords
import io.nekohasekai.pm.database.PmInstance
import io.nekohasekai.pm.database.saveMessage
import td.TdApi

class BlockHandler(pmInstance: PmInstance) : AbstractInputUserFunction(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("block", "unblock", "ban", "unban")

    }

    override suspend fun gc() {

        blocks.clear()

    }

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>,
        originParams: Array<String>
    ) {

        val integration = integration

        if (chatId != admin && chatId != integration?.integration) rejectFunction()

        if (chatId == integration?.integration) {

            if (integration.adminOnly && checkChatAdmin(message)) return

        }

        super.onFunction(userId, chatId, message, function, param, params, originParams)

    }

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        targetUser: Int
    ) {

        val L = L

        if (targetUser == 0) {

            sudo make L.PM_HELP sendTo chatId

            return

        }

        var userHtml: String

        try {

            val targetChat = getChat(targetUser.toLong())

            val chatType = targetChat.type

            if (chatType !is TdApi.ChatTypePrivate) throw TdException(L.USER_NOT_FOUND)

            userHtml = getUser(chatType.userId).htmlInlineMention

        } catch (e: TdException) {

            if (function in arrayOf("block", "ban")) {

                sudo make L.failed { USER_NOT_FOUND } replyTo message

                return

            }

            userHtml = "$targetUser"

        }

        val record = blocks.fetch(targetUser).value == true

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

            saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_OTHER, targetUser.toLong(), notice.id)

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

            saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_OTHER, targetUser.toLong(), notice.id)

        }

    }

}