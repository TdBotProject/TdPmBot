package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.forwardMessages
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.EDITED
import io.github.nekohasekai.pm.MESSAGE_EDITED
import io.github.nekohasekai.pm.database.MessageRecord
import io.github.nekohasekai.pm.database.MessageRecord.Companion.MESSAGE_TYPE_INPUT_FORWARDED
import io.github.nekohasekai.pm.database.MessageRecord.Companion.MESSAGE_TYPE_OUTPUT_MESSAGE
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.and
import td.TdApi

class EditHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onMessageContent(chatId: Long, messageId: Long, newContent: TdApi.MessageContent) {

        val integration = integration

        val useIntegration = integration != null && !integration.paused && chatId != admin

        val record = database {

            MessageRecord.findById(messageId)

        } ?: return

        if ((chatId == admin || useIntegration) && record.type == MESSAGE_TYPE_OUTPUT_MESSAGE) {

            called("admin edited message")

            val targetChat = record.chatId
            val targetMessage = record.targetId!!

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

                sudo make L.EDITED replyTo messageId to chatId send deleteDelayIf(!useIntegration)

            } catch (e: TdException) {

                sudo make L.failed { e.message } replyTo messageId sendTo chatId

            }

            finishEvent()

        } else if (record.type == MessageRecord.MESSAGE_TYPE_INPUT_MESSAGE) {

            userCalled(chatId.toInt(), "guest edited message")

            val targetChat = if (useIntegration) integration!!.integration else admin

            val targetMessage = database {

                MessageRecord.find {

                    (MessageRecords.targetId eq record.messageId
                            ) and (MessageRecords.type eq MESSAGE_TYPE_INPUT_FORWARDED)

                }.firstOrNull()?.messageId

            } ?: 0

            sudo make L.MESSAGE_EDITED replyTo targetMessage syncTo targetChat

            forwardMessages(targetChat, chatId, longArrayOf(messageId), TdApi.SendMessageOptions(), asAlbum = false, sendCopy = false, removeCaption = false)

            finishEvent()

        }

    }

}