package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class MessageRecord(id: EntityID<Long>) : Entity<Long>(id) {

    var _messageId by MessageRecords.messageId
    val messageId get() = _messageId.value
    var type by MessageRecords.type
    var chatId by MessageRecords.chatId
    var targetId by MessageRecords.targetId
    var botId by MessageRecords.botId
    var createAt by MessageRecords.createAt

    companion object : EntityClass<Long, MessageRecord>(MessageRecords) {

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