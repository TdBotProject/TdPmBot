package io.github.nekohasekai.pm.database

import io.github.nekohasekai.nekolib.core.utils.kryo
import org.jetbrains.exposed.dao.id.IdTable
import td.TdApi
import java.util.*

object UserBots : IdTable<Int>("user_bots") {

    val botId = integer("bot_id").entityId()
    val botToken = varchar("bot_token", 64)
    val username = varchar("username", 64).uniqueIndex()
    val owner = integer("owner").index()

    override val id = botId
    override val primaryKey by lazy { PrimaryKey(id) }

}