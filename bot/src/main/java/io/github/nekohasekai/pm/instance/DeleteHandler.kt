package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.deleteDelay
import io.github.nekohasekai.nekolib.core.utils.invoke
import io.github.nekohasekai.nekolib.core.utils.make
import io.github.nekohasekai.nekolib.core.utils.syncDelete
import io.github.nekohasekai.pm.DELETED
import io.github.nekohasekai.pm.MESSAGE_DELETED
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

class DeleteHandler(private val admin: Int, pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onDeleteMessages(chatId: Long, messageIds: LongArray, isPermanent: Boolean, fromCache: Boolean) {

        val records = database {

            messages.find { messageRecords.messageId inList messageIds.toList() }.toList()

        }

        if (records.isEmpty()) return

        if (chatId == admin.toLong()) {

            // 主人删除消息 对等删除

            var success = 0
            var failed = 0

            records.filter {

                it.type in arrayOf(
                        MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED,
                        MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE
                )

            }.forEach {

                try {

                    database {

                        it.delete()

                    }

                    syncDelete(it.chatId, it.targetId!!)

                    success++

                } catch (e: TdException) {

                    failed++

                }

            }

            sudo make L.DELETED to chatId send deleteDelay()

        } else {

            // 用户删除消息, 追加提示.

            records.filter {

                it.type in arrayOf(
                        MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE,
                        MessageRecords.MESSAGE_TYPE_OUTPUT_FORWARDED
                )

            }.forEach { record ->

                database {

                    record.delete()

                    messages.find {

                        ((messageRecords.type eq MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED) or
                                (messageRecords.type eq MessageRecords.MESSAGE_TYPE_OUTPUT_MESSAGE)) and
                                (messageRecords.targetId eq record.messageId)

                    }.forEach {

                        it.delete()

                        sudo make L.MESSAGE_DELETED replyTo it.messageId sendTo admin.toLong() onError null

                    }

                }

            }

        }

        finishEvent()

    }

}