package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.core.*
import io.nekohasekai.ktlib.td.core.*
import io.nekohasekai.ktlib.td.core.extensions.*
import io.nekohasekai.ktlib.td.core.raw.*
import io.nekohasekai.ktlib.td.core.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.*
import io.nekohasekai.pm.manage.menu.IntegrationMenu
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import td.TdApi
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashMap
import kotlin.concurrent.timerTask

class InputHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    var currentUser by AtomicInteger()
    var times by AtomicInteger()

    override fun onLoad() {

        super.onLoad()

        initPersist(PERSIST_UNDER_FUNCTION)

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any?>) {

        if (subId == 0L) {

            val asInput = data.getOrNull(1) as Boolean? == true

            if (asInput) {

                writePersist(userId, PERSIST_UNDER_FUNCTION, 1)

            } else {

                sudo removePersist userId

            }

            onNewMessage(userId, chatId, message, data[0] as String, null)

        } else {

            onNewMessage(userId, chatId, message, "", null)

        }

    }

    val albumMessages = HashMap<Long, AlbumMessages>()

    class AlbumMessages(
            val command: String?
    ) {

        val messages = LinkedList<TdApi.Message>()
        var task: TimerTask? = null

    }

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) = onNewMessage(userId, chatId, message, null, null)

    suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message, command: String?, album: AlbumMessages?) {

        val integration = integration

        val mediaAlbumId = message.mediaAlbumId

        if (admin == chatId || chatId == integration?.integration || !message.fromPrivate) return

        val L = L

        if (mediaAlbumId != 0L && album == null) {

            albumMessages.getOrPut(mediaAlbumId) { AlbumMessages(command) }.apply {

                messages.add(message)

                task?.cancel()

                task = timerTask {

                    GlobalScope.launch(TdClient.eventsContext) {

                        albumMessages.remove(mediaAlbumId)

                        onNewMessage(userId, chatId, message, command, this@apply)

                    }

                }.also {

                    TdClient.timer.schedule(it, 300L)

                }

            }

            return

        }

        suspend fun MessageFactory.syncToTarget(): TdApi.Message? {

            if (integration != null && !integration.paused) {

                try {

                    getChat(integration.integration)

                    return this syncTo integration.integration

                } catch (e: TdException) {

                    database.write {

                        integration.paused = true
                        integration.flush()

                    }

                    launcher.getChatOrNull(admin) ?: return null
                    launcher make L.INTEGRATION_PAUSED_NOTICE.input(me.displayName, me.username) syncTo admin
                    launcher.findHandler<IntegrationMenu>().integrationMenu(L, me.id, userBot, admin.toInt(), admin, 0L, false)

                }

            }

            try {

                getChat(admin)

                return this syncTo admin

            } catch (e: TdException) {

                defaultLog.warn(e, "banned by owner")

                // TODO: PROCESS BOT BANNED BY OWNER

            }

            return null

        }

        val sudo = sudo

        if (sudo is TdPmBot && sudo.public && command == null) {

            sudo.onLaunch(userId, chatId, message)

            finishEvent()

        }

        if (album != null) {

            userCalled(userId, "<< [ Media Group ]")

        } else {

            userCalled(userId, "<< ${message.text ?: "[ ${message.content.javaClass.simpleName.substringAfter("Message")} ]"}")

        }

        var replyTo = message.replyToMessageId

        if (replyTo != 0L) {

            val record = database {

                MessageRecords.select {

                    messagesForCurrentBot and (MessageRecords.messageId eq message.replyToMessageId) and MessageRecords.targetId.isNotNull()

                }.firstOrNull()

            }

            replyTo = if (record != null) {

                record[MessageRecords.targetId]!!

            } else 0L

        }

        // 单条消息回复时未启用双向同步或者媒体组为转发

        if (userId != currentUser || times < 1 || !command.isNullOrBlank() || (
                        replyTo != 0L && (settings?.twoWaySync != true ||
                                (album == null || album.messages[0].forwardInfo != null)))
        ) {

            currentUser = userId
            times = 5

            val user = getUser(userId)

            val inputNotice = if (!command.isNullOrBlank()) {

                sudo makeHtml L.INPUT_FN_NOTICE.input(user.id, user.htmlInlineMention, command)

            } else {

                sudo makeHtml L.INPUT_NOTICE.input(user.id, user.htmlInlineMention)

            }.replyAt(replyTo).syncToTarget() ?: return

            saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_OTHER, chatId, inputNotice.id)

        } else {

            times--

        }

        if (album == null) {

            val forwardedMessage = (sudo make inputForward(message) {

                if (message.forwardInfo == null && settings?.twoWaySync == true) {

                    copyOptions.sendCopy = true

                }

            }).replyAt(replyTo).syncToTarget() ?: return

            saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE, chatId, message.id, forwardedMessage.id)
            saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED, chatId, forwardedMessage.id, message.id)

        } else {

            var messages: Array<TdApi.Message>? = null

            val alwaysForward = settings?.twoWaySync != true

            val content = album.messages.map { if (alwaysForward) it.asForward else it.asInputOrForward }.toTypedArray()
            val ids = album.messages.map { it.id }.toLongArray()

            if (integration != null && !integration.paused) {

                try {

                    getChat(integration.integration)

                    messages = if (settings?.twoWaySync == true && album.messages[0].forwardInfo == null) {

                        sendMessageAlbum(integration.integration, replyTo, TdApi.MessageSendOptions(), content).messages

                    } else {

                        forwardMessages(integration.integration, chatId, ids, TdApi.MessageSendOptions(), asAlbum = true, sendCopy = false, removeCaption = false).messages

                    }

                } catch (e: TdException) {

                    defaultLog.warn(e)

                    database.write {

                        integration.paused = true
                        integration.flush()

                    }

                    launcher.getChatOrNull(admin) ?: return
                    launcher make L.INTEGRATION_PAUSED_NOTICE.input(me.displayName, me.username) syncTo admin
                    launcher.findHandler<IntegrationMenu>().integrationMenu(L, me.id, userBot, admin.toInt(), admin, 0L, false)

                }

            }

            if (messages == null) {

                try {

                    getChat(admin)

                    messages = if (settings?.twoWaySync == true && album.messages[0].forwardInfo == null) {

                        sendMessageAlbum(admin, replyTo, TdApi.MessageSendOptions(), content).messages

                    } else {

                        forwardMessages(admin, chatId, ids, TdApi.MessageSendOptions(), asAlbum = true, sendCopy = false, removeCaption = false).messages

                    }

                } catch (e: TdException) {

                    defaultLog.warn(e, "banned by owner")

                    // TODO: PROCESS BOT BANNED BY OWNER

                    return

                }

            }

            messages!!.forEachIndexed { index, forwardedMessage ->

                saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE, chatId, album.messages[index].id, forwardedMessage.id)
                saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED, chatId, forwardedMessage.id, album.messages[index].id)

            }

        }

        if (album == null) finishEvent()

    }

}