package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.deleteDelayIf
import io.github.nekohasekai.nekolib.core.utils.input
import io.github.nekohasekai.nekolib.core.utils.make
import io.github.nekohasekai.nekolib.core.utils.syncDelete
import io.github.nekohasekai.pm.DELETED
import io.github.nekohasekai.pm.MESSAGE_DELETED
import io.github.nekohasekai.pm.database.MessageRecord
import io.github.nekohasekai.pm.database.MessageRecord.Companion.MESSAGE_TYPE_INPUT_FORWARDED
import io.github.nekohasekai.pm.database.MessageRecord.Companion.MESSAGE_TYPE_OUTPUT_MESSAGE
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

class DeleteHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onDeleteMessages(chatId: Long, messageIds: LongArray, isPermanent: Boolean, fromCache: Boolean) {

        val records = database {

            MessageRecord.find { MessageRecords.messageId inList messageIds.toList() }.toList()

        }

        if (records.isEmpty()) return

        val integration = integration

        val useIntegration = chatId == integration?.integration

        if (chatId == admin || useIntegration) {

            // 主人删除消息 对等删除

            var success = 0
            var failed = 0

            records.filter {

                it.type in arrayOf(
                        MESSAGE_TYPE_INPUT_FORWARDED,
                        MESSAGE_TYPE_OUTPUT_MESSAGE
                )

            }.forEach {

                try {

                    database.write {

                        it.delete()

                    }

                    syncDelete(it.chatId, it.targetId!!)

                    success++

                } catch (e: TdException) {

                    failed++

                }

            }

            sudo make L.DELETED.input(success, success + failed) to chatId send deleteDelayIf(!useIntegration)

        } else {

            // 用户删除消息, 追加提示.

            records.filter {

                it.type in arrayOf(
                        MessageRecord.MESSAGE_TYPE_INPUT_MESSAGE,
                        MessageRecord.MESSAGE_TYPE_OUTPUT_FORWARDED
                )

            }.forEach { record ->

                database.write {

                    record.delete()

                    MessageRecord.find {

                        ((MessageRecords.type eq MESSAGE_TYPE_INPUT_FORWARDED) or
                                (MessageRecords.type eq MESSAGE_TYPE_OUTPUT_MESSAGE)) and
                                (MessageRecords.targetId eq record.messageId)

                    }.forEach {

                        it.delete()

                        sudo make L.MESSAGE_DELETED replyTo it.messageId sendTo admin

                    }

                }

            }

        }

        finishEvent()

    }

}