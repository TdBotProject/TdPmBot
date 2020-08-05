package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.id.LongIdTable

class MessageRecords(botId: Int) : LongIdTable("pm_records_$botId") {

    val messageId = long("messageId").uniqueIndex()
    var type = integer("type")
    var chatId = long("chat_id")
    var targetId = long("target_id").nullable()
    var createAt = integer("create_at")

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