package io.nekohasekai.pm.database

import io.nekohasekai.ktlib.db.IdTableCacheMap
import io.nekohasekai.pm.Launcher
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ActionMessage(id: EntityID<Int>) : Entity<Int>(id) {

    val _userId by ActionMessages.userId
    var userId
        get() = _userId.value
        set(value) {
            _userId._value = value
        }

    var messageId by ActionMessages.messageId
    var createAt by ActionMessages.createAt

    companion object : EntityClass<Int, ActionMessage>(ActionMessages)

    object Cache : IdTableCacheMap<Int, ActionMessage>(Launcher.database, ActionMessage)

}