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

}