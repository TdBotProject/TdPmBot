package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.id.IdTable

object BotIntegrations : IdTable<Int>("bot_integrations") {

    val botId = integer("bot_id").entityId()
    val integration = long("integration")
    val adminOnly = bool("admin_only").default(false)
    val paused = bool("paused").default(false)

    override val id = botId

}