package io.github.nekohasekai.pm.database

import io.github.nekohasekai.nekolib.core.utils.DatabaseCacheMap
import io.github.nekohasekai.pm.Launcher
import org.jetbrains.exposed.sql.*

object UserBlocks : Table("pm_blocks") {

    val botId = integer("bot_id").index()
    val blockedUser = integer("blocked_user")

    override val primaryKey = PrimaryKey(botId, blockedUser)

    class Cache(val botUserId: Int) : DatabaseCacheMap<Int, Boolean>(Launcher.database) {

        override fun read(id: Int): Boolean {

            return UserBlocks.select { (botId eq botUserId) and (blockedUser eq id) }.count() > 0L

        }

        override fun write(id: Int, value: Boolean) {

            if (!value) {

                delete(id)

            } else {

                insert {

                    it[botId] = botUserId
                    it[blockedUser] = id

                }

            }

        }

        override fun delete(id: Int) {

            UserBlocks.deleteWhere { (botId eq botUserId) and (blockedUser eq id) }

        }
    }

}