package io.nekohasekai.pm.database

import io.nekohasekai.ktlib.db.IdTableCacheMap
import io.nekohasekai.pm.Launcher
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class BotSetting(id: EntityID<Int>) : Entity<Int>(id) {

    var _botId by BotSettings.botId
    var botId
        get() = _botId.value
        set(value) {
            _botId._value = value
        }

    var keepActionMessages by BotSettings.keepActionMessages
    var twoWaySync by BotSettings.twoWaySync
    var keepReply by BotSettings.keepReply
    var ignoreDeleteAction by BotSettings.ignoreDeleteAction

    companion object : EntityClass<Int, BotSetting>(BotSettings)

    object Cache : IdTableCacheMap<Int, BotSetting>(Launcher.database, this)

}