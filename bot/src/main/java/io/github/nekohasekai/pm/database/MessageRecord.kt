package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.EntityID

class MessageRecord(table: MessageRecords, id: EntityID<Long>) : Entity<Long>(id) {

    var _messageId by table.messageId
    val messageId get() = _messageId.value
    var type by table.type
    var chatId by table.chatId
    var targetId by table.targetId
    var createAt by table.createAt

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