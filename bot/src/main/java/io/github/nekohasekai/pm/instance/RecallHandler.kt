package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.raw.getUserOrNull
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.pm.MESSAGE_RECALLED_BY
import io.github.nekohasekai.pm.MESSAGE_RECALLED_BY_ME
import io.github.nekohasekai.pm.RECORD_NF
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import td.TdApi

class RecallHandler(pmInstance: PmInstance) : AbstractUserInputHandler(), PmInstance by pmInstance {

    override fun onLoad() {

        initFunction("recall")

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

        val integration = integration

        val currentChat = if (integration?.paused == false) integration.integration else admin

        var deleted = false

        database {

            MessageRecords.select { messagesForCurrentBot and (MessageRecords.chatId eq targetUser.toLong()) }.toList()

        }.apply {

            if (isNotEmpty()) deleted = true

        }.forEach {

            when (it[MessageRecords.type]) {

                MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE,
                MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                    delete(it[MessageRecords.chatId], it[MessageRecords.messageId])

                }

                MessageRecords.MESSAGE_TYPE_INPUT_NOTICE,
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

            val targetMention = getUserOrNull(targetUser)?.asInlineMention ?: ""

            sudo makeHtml L.MESSAGE_RECALLED_BY.input(targetUser.asCode, targetMention, getUser(userId).asInlineMention)

        } else {

            sudo make L.MESSAGE_RECALLED_BY_ME

        } onSuccess deleteDelayIf(settings?.keepActionMessages != true, message) replyTo message

    }
}