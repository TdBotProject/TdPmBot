package io.nekohasekai.pm.database

import org.jetbrains.exposed.sql.Table

open class MessageRecords(override val tableName: String = "pm_message_records") : Table(tableName) {

    val messageId = long("message_id").index()

    val type = integer("type")
    val chatId = long("chat_id").index()
    val botId = integer("bot_id").index()
    val targetId = long("target_id").nullable()
    val createAt = integer("create_at")

    override val primaryKey = PrimaryKey(botId, chatId, messageId)

    companion object : MessageRecords() {

        // 输入消息 (chatId: 用户, targetId: 0)
        const val MESSAGE_TYPE_INPUT_MESSAGE = 1

        // 收入提示 (chatId: 用户, targetId: 0)
        const val MESSAGE_TYPE_INPUT_OTHER = 2

        // 收入消息 (chatId: 用户, targetId: 消息 ID)
        const val MESSAGE_TYPE_INPUT_FORWARDED = 3

        // 输出消息 (chatId: 用户, targetId: 消息 ID)
        const val MESSAGE_TYPE_OUTPUT_FORWARDED = 4

        // 发送消息 (chatId: 用户, targetId: 消息 ID)
        const val MESSAGE_TYPE_OUTPUT_MESSAGE = 5

    }


}