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


}