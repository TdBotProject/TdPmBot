package io.github.nekohasekai.pm.database

import io.github.nekohasekai.nekolib.core.utils.IdTableCacheMap
import io.github.nekohasekai.pm.Launcher
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class BotSetting(id: EntityID<Int>) : Entity<Int>(id) {

    var botId by BotSettings.botId
    var keepActionMessages by BotSettings.keepActionMessages
    var twoWaySync by BotSettings.twoWaySync
    var keepReply by BotSettings.keepReply
    var ignoreDeleteAction by BotSettings.ignoreDeleteAction

    companion object : EntityClass<Int, BotSetting>(BotSettings)

    object Cache : IdTableCacheMap<Int, BotSetting>(Launcher.database, this)

}