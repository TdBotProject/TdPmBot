package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.id.IdTable

object BotIntegrations : IdTable<Int>("bot_integrations") {

    val botId = integer("bot_id").entityId()
    val integration = long("integration")
    val adminOnly = bool("admin_only")
    val paused = bool("paused")

    override val id = botId

}