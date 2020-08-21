package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.PmInstance
import td.TdApi

class JoinHandler(pmInstance: PmInstance) : AbstractUserInputHandler(), PmInstance by pmInstance {

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

            super.onFunction(userId, chatId, message, function, param, params, originParams)

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

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, targetUser: Int) {

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

        sudo makeHtml L.JOINED_NOTICE.input(user.asIdMention, user.displayNameHtml.asCode) sendTo chatId

    }

}
