package io.nekohasekai.pm.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class BotIntegration(id: EntityID<Int>) : Entity<Int>(id) {

    var _botId by BotIntegrations.botId
    var botId
        get() = _botId.value
        set(value) {
            _botId._value = value
        }

    var integration by BotIntegrations.integration
    var adminOnly by BotIntegrations.adminOnly
    var paused by BotIntegrations.paused

    companion object : EntityClass<Int, BotIntegration>(BotIntegrations)


}