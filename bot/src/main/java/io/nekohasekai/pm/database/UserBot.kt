package io.nekohasekai.pm.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class UserBot(id: EntityID<Int>) : Entity<Int>(id) {

    var _botId by UserBots.botId
    var botId
        get() = _botId.value
        set(value) {
            _botId._value = value
        }

    var username by UserBots.username
    var owner by UserBots.owner
    var botToken by UserBots.botToken

    companion object : EntityClass<Int, UserBot>(UserBots)

}