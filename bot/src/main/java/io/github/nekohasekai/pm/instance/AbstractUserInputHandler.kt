package io.github.nekohasekai.pm.instance

import cn.hutool.core.util.NumberUtil
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.searchPublicChatOrNull
import io.github.nekohasekai.nekolib.core.utils.deleteDelay
import io.github.nekohasekai.nekolib.core.utils.entities
import io.github.nekohasekai.nekolib.core.utils.make
import io.github.nekohasekai.nekolib.core.utils.text
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.nekolib.i18n.failed
import io.github.nekohasekai.pm.RECORD_NF
import io.github.nekohasekai.pm.database.MessageRecords
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import td.TdApi

abstract class AbstractUserInputHandler : TdHandler() {

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        val L = LocaleController.forChat(userId)

        var targetUser = 0

        if (message.replyToMessageId != 0L) {

            val record = database {

                MessageRecords.select { messagesForCurrentBot and (MessageRecords.messageId eq message.replyToMessageId) }.firstOrNull()

            }

            if (record == null) {

                sudo make L.failed { RECORD_NF } onSuccess deleteDelay(message) sendTo chatId

                return

            }

            targetUser = record[MessageRecords.chatId].toInt()

        } else {

            for (entity in message.entities!!) {

                if (entity.type is TdApi.TextEntityTypeMention) {

                    val username = message.text!!.substring(entity.offset, entity.offset + entity.length).substringAfter("@")

                    val user = searchPublicChatOrNull(username) ?: continue

                    targetUser = user.id.toInt()

                    break

                } else if (entity.type is TdApi.TextEntityTypeMentionName) {

                    targetUser = (entity.type as TdApi.TextEntityTypeMentionName).userId

                    break

                }

            }

        }

        if (targetUser == 0 && NumberUtil.isInteger(param)) {

            targetUser = param.toInt()

        }

        onFunction(userId, chatId, message, function, targetUser)

    }

    abstract suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, targetUser: Int)

}