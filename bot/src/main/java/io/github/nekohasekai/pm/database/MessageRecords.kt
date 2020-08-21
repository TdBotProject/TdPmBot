package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.sql.Table

object MessageRecords : Table("pm_message_records") {

    val messageId = long("message_id").index()
    val type = integer("type")
    val chatId = long("chat_id").index()
    val botId = integer("bot_id").index()
    val targetId = long("target_id").index().nullable()
    val createAt = integer("create_at")

    override val primaryKey = PrimaryKey(messageId, botId)

    // 输入消息 (chatId: 用户, targetId: 0)
    const val MESSAGE_TYPE_INPUT_MESSAGE = 1

    // 收入提示 (chatId: 用户, targetId: 0)
    const val MESSAGE_TYPE_INPUT_NOTICE = 2

    // 收入消息 (chatId: 用户, targetId: 消息 ID)
    const val MESSAGE_TYPE_INPUT_FORWARDED = 3

    // 输出消息 (chatId: 用户, targetId: 消息 ID)
    const val MESSAGE_TYPE_OUTPUT_FORWARDED = 4

    // 发送消息 (chatId: 用户, targetId: 消息 ID)
    const val MESSAGE_TYPE_OUTPUT_MESSAGE = 5

}