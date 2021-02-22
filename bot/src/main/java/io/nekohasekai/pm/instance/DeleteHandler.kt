package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.core.raw.getMessageWith
import io.nekohasekai.ktlib.td.utils.delete
import io.nekohasekai.ktlib.td.utils.deleteDelayIf
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.syncDelete
import io.nekohasekai.pm.MESSAGE_DELETED
import io.nekohasekai.pm.MESSAGE_DELETED_BY_ME
import io.nekohasekai.pm.database.L
import io.nekohasekai.pm.database.MessageRecords
import io.nekohasekai.pm.database.PmInstance
import io.nekohasekai.pm.messagesForCurrentBot
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select

class DeleteHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onDeleteMessages(
        chatId: Long,
        messageIds: LongArray,
        isPermanent: Boolean,
        fromCache: Boolean
    ) {

        if (!sudo.waitForAuth() || !isPermanent || fromCache) return

        val records = database {

            MessageRecords.select { messagesForCurrentBot and (MessageRecords.messageId inList messageIds.toList()) }
                .toList()

        }

        if (records.isEmpty()) return

        val integration = integration

        val L = L

        if (chatId == admin || chatId == integration?.integration) {

            if (settings?.ignoreDeleteAction == true) return

            // 主人删除消息 对等删除

            var success = 0
            var failed = 0

            records.filter {

                it[MessageRecords.type] in arrayOf(
                    MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
                    MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE
                )

            }.forEach {

                try {

                    database.write {

                        MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.messageId eq it[MessageRecords.messageId]) }

                    }

                    syncDelete(it[MessageRecords.chatId], it[MessageRecords.targetId]!!)

                    success++

                } catch (e: TdException) {

                    failed++

                }

            }

            if (success + failed > 0) {

                sudo make L.MESSAGE_DELETED_BY_ME.input(
                    success,
                    success + failed
                ) onSuccess deleteDelayIf(settings?.keepActionMessages != true) sendTo chatId

            }

        } else {

            // 用户删除消息, 追加提示 / 删除.

            val twoWaySync = settings?.twoWaySync == true

            records.filter {

                it[MessageRecords.type] in arrayOf(
                    MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE,
                    MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED
                )

            }.forEach { row ->

                database.write {

                    MessageRecords.deleteWhere { messagesForCurrentBot and (MessageRecords.messageId eq row[MessageRecords.messageId]) }

                    if (twoWaySync) {

                        delete(
                            if (integration?.paused == false) integration.integration else admin,
                            row[MessageRecords.targetId]!!
                        )

                    } else {

                        getMessageWith(
                            if (integration?.paused == false) integration.integration else admin,
                            row[MessageRecords.targetId]!!
                        ) {

                            onSuccess {

                                sudo make L.MESSAGE_DELETED replyTo it

                            }

                            onFailure = null

                        }

                    }

                }

            }

        }

    }

}