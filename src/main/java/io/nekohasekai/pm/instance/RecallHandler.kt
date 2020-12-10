package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.raw.getUser
import io.nekohasekai.ktlib.td.core.raw.getUserOrNull
import io.nekohasekai.ktlib.td.extensions.htmlCode
import io.nekohasekai.ktlib.td.extensions.htmlInlineMention
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.MESSAGE_RECALLED_BY
import io.nekohasekai.pm.MESSAGE_RECALLED_BY_ME
import io.nekohasekai.pm.RECORD_NF
import io.nekohasekai.pm.database.L
import io.nekohasekai.pm.database.MessageRecords
import io.nekohasekai.pm.database.PmInstance
import io.nekohasekai.pm.messagesForCurrentBot
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import td.TdApi

class RecallHandler(pmInstance: PmInstance) : AbstractInputUserFunction(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("recall")

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

        val integration = integration

        val currentChat = if (integration?.paused == false) integration.integration else admin

        val L = L

        val messagesToDelete = database {

            MessageRecords.select { messagesForCurrentBot and (MessageRecords.chatId eq targetUser.toLong()) }.toList()

        }

        val deleted = messagesToDelete.isNotEmpty()

        messagesToDelete.forEach {
            when (it[MessageRecords.type]) {

                MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE,
                MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                    delete(it[MessageRecords.chatId], it[MessageRecords.messageId])

                }

                MessageRecords.MESSAGE_TYPE_INPUT_OTHER,
                MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
                MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE -> {

                    delete(currentChat, it[MessageRecords.messageId])

                }

            }
        }

        if (!deleted) {

            sudo make L.RECORD_NF replyTo message

            return

        }

        database.write {

            MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.chatId eq targetUser.toLong()) }

        }

        if (integration?.paused == false) {

            val targetMention = getUserOrNull(targetUser)?.htmlInlineMention ?: ""

            sudo makeHtml L.MESSAGE_RECALLED_BY.input(
                targetUser.htmlCode,
                targetMention,
                getUser(userId).htmlInlineMention
            )

        } else {

            sudo make L.MESSAGE_RECALLED_BY_ME

        } onSuccess deleteDelayIf(settings?.keepActionMessages != true, message) replyTo message

    }
}