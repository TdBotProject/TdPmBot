package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.core.escapeHtmlTags
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.raw.getChat
import io.nekohasekai.ktlib.td.core.raw.getUser
import io.nekohasekai.ktlib.td.extensions.displayName
import io.nekohasekai.ktlib.td.extensions.htmlCode
import io.nekohasekai.ktlib.td.extensions.htmlIdMention
import io.nekohasekai.ktlib.td.i18n.failed
import io.nekohasekai.ktlib.td.utils.checkChatAdmin
import io.nekohasekai.ktlib.td.utils.deleteDelay
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.L
import io.nekohasekai.pm.database.PmInstance
import td.TdApi

class JoinHandler(pmInstance: PmInstance) : AbstractInputUserFunction(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("join", "exit")

    }

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>
    ) {

        val integration = integration

        val L = L

        if (chatId != admin && chatId != integration?.integration) rejectFunction()

        if (chatId == integration?.integration) {

            if (integration.adminOnly && checkChatAdmin(message)) return

        }

        if (function == "join") {

            super.onFunction(userId, chatId, message, function, param, params)

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

        val targetChat = try {

            getChat(targetUser.toLong())

        } catch (e: TdException) {

            sudo make L.failed { USER_NOT_FOUND } onSuccess deleteDelay(message) sendTo chatId

            return

        }

        val chatType = targetChat.type

        if (chatType !is TdApi.ChatTypePrivate) {

            sudo make L.failed { USER_NOT_FOUND } replyTo message

            return

        }

        val user = try {

            getUser(targetUser)

        } catch (e: TdException) {

            sudo make L.failed { USER_NOT_FOUND } replyTo message

            return

        }

        findHandler<OutputHandler>().currentChat = targetChat.id

        sudo makeHtml L.JOINED_NOTICE.input(
            user.htmlIdMention,
            user.displayName.escapeHtmlTags().htmlCode
        ) sendTo chatId

    }

}
