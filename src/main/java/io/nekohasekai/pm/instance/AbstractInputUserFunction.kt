package io.nekohasekai.pm.instance

import cn.hutool.core.util.NumberUtil
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.core.raw.searchPublicChatOrNull
import io.nekohasekai.ktlib.td.extensions.entities
import io.nekohasekai.ktlib.td.extensions.text
import io.nekohasekai.ktlib.td.i18n.failed
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.ktlib.td.utils.deleteDelay
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.pm.RECORD_NF
import io.nekohasekai.pm.database.MessageRecords
import io.nekohasekai.pm.messagesForCurrentBot
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import td.TdApi

abstract class AbstractInputUserFunction : TdHandler() {

    override suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        param: String,
        params: Array<String>,
        originParams: Array<String>
    ) {

        val L = localeFor(userId)

        var targetUser = 0

        if (message.replyToMessageId != 0L) {

            val record = database {

                MessageRecords.select { messagesForCurrentBot and (MessageRecords.messageId eq message.replyToMessageId) }
                    .firstOrNull()

            }

            if (record == null) {

                sudo make L.failed { RECORD_NF } onSuccess deleteDelay(message) sendTo chatId

                return

            }

            targetUser = record[MessageRecords.chatId].toInt()

        } else {

            for (entity in message.entities!!) {

                if (entity.type is TdApi.TextEntityTypeMention) {

                    val username =
                        message.text!!.substring(entity.offset, entity.offset + entity.length).substringAfter("@")

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

    abstract suspend fun onFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        function: String,
        targetUser: Int
    )

}