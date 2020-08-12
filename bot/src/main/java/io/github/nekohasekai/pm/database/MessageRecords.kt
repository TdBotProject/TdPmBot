package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.id.IdTable

object MessageRecords : IdTable<Long>("pm_records") {

    val messageId = long("message_id").entityId()
    val type = integer("type").index()
    val chatId = long("chat_id").index()
    val botId = integer("bot_id").index()
    val targetId = long("target_id").index().nullable()
    val createAt = integer("create_at")

    override val id = messageId
    override val primaryKey by lazy { PrimaryKey(id) }

}