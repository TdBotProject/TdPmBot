package io.nekohasekai.pm.instance

import com.esotericsoftware.kryo.KryoException
import io.nekohasekai.ktlib.core.*
import io.nekohasekai.ktlib.td.core.*
import io.nekohasekai.ktlib.td.core.extensions.*
import io.nekohasekai.ktlib.td.core.i18n.*
import io.nekohasekai.ktlib.td.core.raw.*
import io.nekohasekai.ktlib.td.core.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import td.TdApi
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.timerTask

class OutputHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    companion object {

        const val dataId = DATA_DELETE_MESSAGE

    }

    override fun onLoad() {

        initData(dataId)

    }

    var currentChat = 0L

    val albumMessages = HashMap<Long, AlbumMessages>()

    class AlbumMessages(val albumId: Long) {

        val messages = LinkedList<TdApi.Message>()
        var task: TimerTask? = null

    }

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) = onNewMessage(userId, chatId, message, null)

    suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message, album: AlbumMessages?) {

        val integration = integration

        val useIntegration = chatId == integration?.integration

        if (chatId != admin && !useIntegration) return

        val mediaAlbumId = message.mediaAlbumId

        val L = L

        suspend fun delayAlbum(): Nothing {

            albumMessages.getOrPut(mediaAlbumId) { AlbumMessages(mediaAlbumId) }.apply {

                messages.add(message)

                task?.cancel()

                task = timerTask {

                    GlobalScope.launch(TdClient.eventsContext) {

                        albumMessages.remove(mediaAlbumId)

                        onNewMessage(userId, chatId, message, this@apply)

                    }

                }.also {

                    TdClient.timer.schedule(it, 1000L)

                }

            }

            finishEvent()

        }

        suspend fun execMessages(targetChat: Long, replyTo: Long) {

            val messages = if (album == null) {

                try {

                    val sentMessage = sudo make message.asInputOrForward replyAt replyTo syncTo targetChat

                    saveMessage(MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE, targetChat, message.id, sentMessage.id)
                    saveMessage(MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED, targetChat, sentMessage.id, message.id)

                    userCalled(userId, ">> ${sentMessage.text ?: "[ ${sentMessage.content.javaClass.simpleName.substringAfter("Message")} ]"}")

                    arrayOf(sentMessage)

                } catch (e: TdException) {

                    sudo make L.failed { e.message } onSuccess deleteDelayIf(settings?.keepActionMessages != true) replyTo message

                    return

                }

            } else {

                try {

                    val messages = sendMessageAlbum(targetChat, replyTo, TdApi.MessageSendOptions(), album.messages.map { it.asInputOrForward }.toTypedArray()).messages

                    messages.forEachIndexed { index, sentMessage ->

                        saveMessage(MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE, targetChat, album.messages[index].id, sentMessage.id)
                        saveMessage(MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED, targetChat, sentMessage.id, album.messages[index].id)

                    }

                    userCalled(userId, ">> [ Media Group ]")

                    messages

                } catch (e: TdException) {

                    sudo make L.failed { e.message } onSuccess deleteDelayIf(settings?.keepActionMessages != true) replyTo message

                    return

                }

            }

            (sudo make L.SENT).apply {

                if (settings?.keepActionMessages == true) {

                    if (settings.ignoreDeleteAction) {

                        withMarkup(inlineButton {

                            dataLine(L.DELETE, dataId, targetChat.asByteArray(), messages.map { it.id }.toLongArray().toByteArray())

                        })

                    }

                } else {

                    onSuccess = deleteDelay()

                }

            } replyTo if (album == null) message else album.messages[0]

            finishWithDelay()

        }

        if (message.replyToMessageId == 0L) {

            if (currentChat == 0L) {

                if (chatId == admin) {

                    sudo make L.PM_HELP syncReplyTo message

                }

                return

            }

            if (mediaAlbumId != 0L && album == null) {

                delayAlbum()

            }

            if (useIntegration && integration!!.adminOnly && checkChatAdmin(message)) finishEvent()

            execMessages(currentChat, 0L)

        }

        if (mediaAlbumId != 0L && album == null) {

            delayAlbum()

        }

        val record = database {

            MessageRecords.select { messagesForCurrentBot and (MessageRecords.messageId eq message.replyToMessageId) }.firstOrNull()

        }

        if (record == null) {

            if (useIntegration && getMessageOrNull(chatId, message.replyToMessageId)?.let { getChatMemberOrNull(chatId, it.senderUserId)?.status?.isMember } == true) {

                // 如果回复的用户在群组里即跳过找不到记录提示

                return

            }

            sudo make L.RECORD_NF onSuccess deleteDelayIf(settings?.keepActionMessages != true) replyTo message

            return

        }

        suspend fun getTargetChat() = try {

            getChat(record[MessageRecords.chatId]).id

        } catch (e: TdException) {

            sudo make L.failed { USER_NOT_FOUND } onSuccess deleteDelayIf(settings?.keepActionMessages != true) replyTo message

            null

        }

        when (record[MessageRecords.type]) {

            MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE -> {

                defaultLog.warn("Please delete the data after changing the bot admin.")

            }

            MessageRecords.MESSAGE_TYPE_INPUT_OTHER -> {

                execMessages(getTargetChat() ?: return, 0L)

            }

            MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
            MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE -> {

                val targetChat = getTargetChat() ?: return

                val reply = currentChat == targetChat || settings?.keepReply == true
                var targetMessage = 0L

                if (reply) {

                    targetMessage = try {

                        getMessage(targetChat, record[MessageRecords.targetId]!!).id

                    } catch (e: TdException) {

                        sudo make L.failed { REPLIED_NF } onSuccess deleteDelayIf(settings?.keepActionMessages != true) replyTo message

                        return

                    }

                }

                execMessages(targetChat, targetMessage)

            }

        }

    }

    override suspend fun onNewCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>) {

        val useIntegration = chatId == integration?.integration

        if (chatId != admin && !useIntegration) return

        if (useIntegration && integration!!.adminOnly && checkChatAdmin(chatId, userId, queryId)) return

        val L = L

        val targetChat = data[0].toLong()

        val targetMessages = try {

            data[1].formByteArray()

        } catch (e: KryoException) {

            longArrayOf(data[1].toLong())

            // TODO: Remove this

        }

        database.write {

            MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.messageId inList listOf(messageId, * targetMessages.toTypedArray())) }

        }

        targetMessages.forEachIndexed { index, targetMessage ->

            getMessageWith(targetChat, targetMessage) {

                onSuccess {

                    if (index == 0) {

                        sudo makeAnswer L.DELETED answerTo queryId

                        if (useIntegration) {

                            sudo makeHtml L.MESSAGE_DELETED_BY.input(getUser(userId).htmlInlineMention) at messageId editTo chatId

                        } else {

                            sudo make L.MESSAGE_DELETED_BY_ME at messageId editTo chatId

                        }

                    }

                    sudo delete it

                }

                onFailure {

                    if (index == 0) {

                        sudo makeAlert L.RECORD_NF answerTo queryId

                        editMessageReplyMarkupOrNull(chatId, messageId, null)

                    }

                }

            }

        }

    }

}