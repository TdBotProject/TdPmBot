package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.id.IdTable

object BotSettings : IdTable<Int>("bots_settings") {

    val botId = integer("bot_id").entityId()

    val keepActionMessages = bool("keep_action_messages").default(false)
    val twoWaySync = bool("two_way_sync").default(false)
    val ignoreDeleteAction = bool("ignore_delete_action").default(false)


    override val id = botId

}