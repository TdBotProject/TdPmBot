package io.nekohasekai.pm.database

import org.jetbrains.exposed.dao.id.IdTable

object UserBots : IdTable<Int>("user_bots") {

    val botId = integer("bot_id").entityId()

    val botToken = varchar("bot_token", 64)
    val username = varchar("username", 64).uniqueIndex()
    val owner = integer("owner").index()

    override val id = botId
    override val primaryKey by lazy { PrimaryKey(id) }

}