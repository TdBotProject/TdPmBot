package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.extensions.asInput
import io.nekohasekai.ktlib.td.i18n.failed
import io.nekohasekai.ktlib.td.core.raw.forwardMessages
import io.nekohasekai.ktlib.td.core.raw.getMessageOrNull
import io.nekohasekai.ktlib.td.utils.deleteDelayIf
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.pm.EDITED
import io.nekohasekai.pm.MESSAGE_EDITED
import io.nekohasekai.pm.database.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import td.TdApi

class EditHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onMessageContent(chatId: Long, messageId: Long, newContent: TdApi.MessageContent) {

        if (!sudo.waitForAuth()) return

        val integration = integration

        val useIntegration = integration != null && !integration.paused && chatId != admin

        val L = L

        val record = database {

            MessageRecords.select { messagesForCurrentBot and (MessageRecords.messageId eq messageId) }.firstOrNull()

        } ?: return

        if (getMessageOrNull(chatId, messageId)?.senderUserId ?: me.id == me.id) return

        if ((chatId == admin || useIntegration) && record[MessageRecords.type] == MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE) {

            val targetChat = record[MessageRecords.chatId]
            val targetMessage = record[MessageRecords.targetId]!!

            // 同步编辑消息

            val edit = if (newContent is TdApi.MessageText) {

                sudo make newContent.text to targetChat mkEditAt targetMessage

            } else if (newContent is TdApi.MessageAnimation || newContent is TdApi.MessageAudio || newContent is TdApi.MessageDocument || newContent is TdApi.MessagePhoto || newContent is TdApi.MessageVideo) {

                TdApi.EditMessageMedia(targetChat, targetMessage, null, newContent.asInput)

            } else if (newContent is TdApi.MessageVoiceNote) {

                TdApi.EditMessageCaption(targetChat, targetMessage, null, newContent.caption)

            } else if (newContent is TdApi.MessageLocation) {

                TdApi.EditMessageLiveLocation(targetChat, targetMessage, null, newContent.location)

            } else {

                return

            }

            try {

                syncUnit(edit)

                sudo make L.EDITED replyAt messageId onSuccess deleteDelayIf(settings?.keepActionMessages != true) sendTo chatId

            } catch (e: TdException) {

                sudo make L.failed { e.message } replyAt messageId sendTo chatId

            }

            finishEvent()

        } else if (record[MessageRecords.type] == MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE) {

            val targetChat = if (useIntegration) integration!!.integration else admin

            val targetMessage = database {

                MessageRecords.select {

                    messagesForCurrentBot and (
                            MessageRecords.targetId eq record[MessageRecords.messageId]
                            ) and (MessageRecords.type eq MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED)

                }.firstOrNull()?.let { it[MessageRecords.messageId] }

            } ?: 0

            if (settings?.twoWaySync == true) {

                val edit = if (newContent is TdApi.MessageText) {

                    sudo make newContent.text to targetChat mkEditAt targetMessage

                } else if (newContent is TdApi.MessageAnimation || newContent is TdApi.MessageAudio || newContent is TdApi.MessageDocument || newContent is TdApi.MessagePhoto || newContent is TdApi.MessageVideo) {

                    TdApi.EditMessageMedia(targetChat, targetMessage, null, newContent.asInput)

                } else if (newContent is TdApi.MessageVoiceNote) {

                    TdApi.EditMessageCaption(targetChat, targetMessage, null, newContent.caption)

                } else if (newContent is TdApi.MessageLocation) {

                    TdApi.EditMessageLiveLocation(targetChat, targetMessage, null, newContent.location)

                } else {

                    return

                }

                try {

                    syncUnit(edit)

                    return

                } catch (e: TdException) {
                }

            }

            val status = sudo make L.MESSAGE_EDITED replyAt targetMessage syncTo targetChat

            saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_OTHER, chatId, status.id)

            val forwarded = forwardMessages(targetChat, chatId, longArrayOf(messageId), TdApi.MessageSendOptions(), asAlbum = false, sendCopy = false, removeCaption = false).messages[0].id

            saveMessage(MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED, chatId, forwarded)

            finishEvent()

        }

    }

}