package io.github.nekohasekai.pm.database

import io.github.nekohasekai.nekolib.core.utils.IdTableCacheMap
import io.github.nekohasekai.pm.Launcher
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class BotIntegration(id: EntityID<Int>) : Entity<Int>(id) {

    var botId by BotIntegrations.botId
    var integration by BotIntegrations.integration
    var adminOnly by BotIntegrations.adminOnly
    var paused by BotIntegrations.paused

    companion object : EntityClass<Int, BotIntegration>(BotIntegrations)

    object Cache : IdTableCacheMap<Int, BotIntegration>(Launcher.database, this)

}