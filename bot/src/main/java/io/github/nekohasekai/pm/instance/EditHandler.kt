package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.forwardMessages
import io.github.nekohasekai.nekolib.core.utils.asInput
import io.github.nekohasekai.nekolib.core.utils.deleteDelayIf
import io.github.nekohasekai.nekolib.core.utils.make
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.EDITED
import io.github.nekohasekai.pm.MESSAGE_EDITED
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import td.TdApi

class EditHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onMessageContent(chatId: Long, messageId: Long, newContent: TdApi.MessageContent) {

        val integration = integration

        val useIntegration = integration != null && !integration.paused && chatId != admin

        val record = database {

            MessageRecords.select { currentBot and (MessageRecords.messageId eq messageId) }.firstOrNull()

        } ?: return

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

                sudo make L.EDITED replyAt messageId onSuccess deleteDelayIf(!useIntegration) sendTo chatId

            } catch (e: TdException) {

                sudo make L.failed { e.message } replyAt messageId sendTo chatId

            }

            finishEvent()

        } else if (record[MessageRecords.type] == MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE) {

            val targetChat = if (useIntegration) integration!!.integration else admin

            val targetMessage = database {

                MessageRecords.select {

                    currentBot and (
                            MessageRecords.targetId eq record[MessageRecords.messageId]
                            ) and (MessageRecords.type eq MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED)

                }.firstOrNull()?.let { it[MessageRecords.messageId] }

            } ?: 0

            sudo make L.MESSAGE_EDITED replyAt targetMessage syncTo targetChat

            forwardMessages(targetChat, chatId, longArrayOf(messageId), TdApi.SendMessageOptions(), asAlbum = false, sendCopy = false, removeCaption = false)

            finishEvent()

        }

    }

}