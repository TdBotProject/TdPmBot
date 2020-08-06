package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.id.IdTable

class MessageRecords(botId: Int) : IdTable<Long>("pm_records_$botId") {

    val messageId = long("message_id").entityId()
    val type = integer("type")
    val chatId = long("chat_id")
    val targetId = long("target_id").index().nullable()
    val createAt = integer("create_at")

    override val id = messageId
    override val primaryKey by lazy { PrimaryKey(id) }

    companion object {

        // 输入消息 (chatId: 用户, targetId: 0)
        const val MESSAGE_TYPE_INPUT_MESSAGE = 1

        // 收入提示 (chatId: 用户, targetId: 0)
        const val MESSAGE_TYPE_INPUT_NOTICE = 2

        // 收入消息 (chatId: 用户, targetId: 消息 ID)
        const val MESSAGE_TYPE_INPUT_FORWARDED = 3

        // 发送消息 (chatId: 用户, targetId: 消息 ID)
        const val MESSAGE_TYPE_OUTPUT_MESSAGE = 3

        // 输出消息 (chatId: 用户, targetId: 消息 ID)
        const val MESSAGE_TYPE_OUTPUT_FORWARDED = 4

    }

}