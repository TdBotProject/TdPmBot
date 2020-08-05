package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.EntityID

class MessageRecord(table: MessageRecords, id: EntityID<Long>) : Entity<Long>(id) {

    var messageId by table.messageId
    var type by table.type
    var chatId by table.chatId
    var targetId by table.targetId
    var createAt by table.createAt

}