package io.nekohasekai.pm.database

import cn.hutool.core.date.DateUtil
import cn.hutool.core.date.SystemClock
import io.nekohasekai.ktlib.core.defaultLog
import io.nekohasekai.ktlib.core.receive
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.extensions.displayName
import io.nekohasekai.ktlib.td.i18n.LocaleController
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.ktlib.td.core.raw.getChat
import io.nekohasekai.ktlib.td.core.raw.getMessageOrNull
import io.nekohasekai.pm.instance.messagesForCurrentBot
import io.nekohasekai.pm.launcher
import org.jetbrains.exposed.sql.*

interface PmInstance {

    val admin: Long
    val integration: BotIntegration?
    val settings: BotSetting?
    val blocks: UserBlocks.Cache

}

val PmInstance.L by receive<PmInstance, LocaleController> { (this as TdHandler).launcher.localeFor(admin) }

fun TdHandler.saveMessage(type: Int, chatId: Long, messageId: Long, targetId: Long? = null) {

    database.write {

        MessageRecords.insert {

            it[MessageRecords.messageId] = messageId

            it[MessageRecords.type] = type

            it[MessageRecords.chatId] = chatId

            if (targetId != null) it[MessageRecords.targetId] = targetId

            it[createAt] = (SystemClock.now() / 100L).toInt()

            it[botId] = me.id

        }

    }

}

suspend fun TdHandler.gc(instance: PmInstance) {

    instance.blocks.gc()

    var deleted = 0

    val integration = instance.integration

    val query = MessageRecords.select { messagesForCurrentBot and (MessageRecords.createAt less (DateUtil.yesterday().time / 100)) }

    for (row in database { query.iterator() }) {

        when (row[MessageRecords.type]) {

            MessageRecords.MESSAGE_TYPE_INPUT_OTHER,
            MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
            MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE -> {

                val chatId = row[MessageRecords.chatId]
                val messageId = row[MessageRecords.messageId]

                try {

                    if (integration?.paused == false) {

                        getChat(integration.integration)

                        if (getMessageOrNull(integration.integration, messageId) != null) continue

                    }

                    getChat(instance.admin)

                    if (getMessageOrNull(instance.admin, messageId) != null) continue

                } catch (ignored: TdException) {
                }

                deleted++

                database.write {

                    MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.chatId eq chatId) and (MessageRecords.messageId eq messageId) }

                }

            }

        }

    }

    for (row in database { query.iterator() }) {

        when (row[MessageRecords.type]) {

            MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE,
            MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED -> {

                val chatId = row[MessageRecords.chatId]
                val messageId = row[MessageRecords.messageId]

                if (database { MessageRecords.select { messagesForCurrentBot and (MessageRecords.chatId eq chatId) and (MessageRecords.targetId eq messageId) }.firstOrNull() } != null) {

                    if (getMessageOrNull(row[MessageRecords.chatId], messageId) != null) continue

                }

                deleted++

                database.write {

                    MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.chatId eq chatId) and (MessageRecords.messageId eq messageId) }

                }

            }

        }

    }

    if (deleted > 0) {

        defaultLog.info("[${me.displayName}] 回收了 $deleted 条 无效消息.")

    }

}