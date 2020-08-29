package io.github.nekohasekai.pm.database

import cn.hutool.core.date.DateUtil
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getMessageOrNull
import io.github.nekohasekai.nekolib.core.utils.defaultLog
import io.github.nekohasekai.nekolib.core.utils.displayName
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.instance.messagesForCurrentBot
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select

interface PmInstance {

    val L: LocaleController
    val admin: Long
    val integration: BotIntegration?
    val settings: BotSetting?
    val blocks: UserBlocks.Cache

}

suspend fun TdHandler.gc(instance: PmInstance) {

    var deleted = 0

    val integration = instance.integration

    val query = MessageRecords.select { messagesForCurrentBot and (MessageRecords.createAt less (DateUtil.yesterday().time / 100)) }

    for (row in database { query.iterator() }) {

        when (row[MessageRecords.type]) {

            MessageRecords.MESSAGE_TYPE_INPUT_NOTICE,
            MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
            MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE -> {

                val messageId = row[MessageRecords.messageId]

                if (integration?.paused == false) {

                    getChat(integration.integration)

                    if (getMessageOrNull(integration.integration, messageId) != null) continue

                }

                if (getMessageOrNull(instance.admin, messageId) != null) continue

                deleted++

                database.write {

                    MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.messageId eq messageId) }

                }

            }

        }

    }

    for (row in database { query.iterator() }) {

        when (row[MessageRecords.type]) {

            MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE,
            MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                val messageId = row[MessageRecords.messageId]

                if (database { MessageRecords.select { messagesForCurrentBot and (MessageRecords.targetId eq messageId) }.firstOrNull() } != null) {

                    if (getMessageOrNull(row[MessageRecords.chatId], messageId) != null) continue

                }

                deleted++

                database.write {

                    MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.messageId eq messageId) }

                }

            }

        }

    }

    if (deleted > 0) {

        defaultLog.info("[${me.displayName}] 回收了 $deleted 条 无效消息.")

    }

}