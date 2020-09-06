package io.nekohasekai.pm.database

import io.nekohasekai.ktlib.db.DatabaseCacheMap
import io.nekohasekai.ktlib.db.kryo
import io.nekohasekai.ktlib.db.upsert
import io.nekohasekai.pm.Launcher
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import td.TdApi
import java.util.*

object StartMessages : Table("pm_start_messages") {

    val botId = integer("botId").uniqueIndex()

    val messages = kryo<LinkedList<TdApi.InputMessageContent>>("messages")

    object Cache : DatabaseCacheMap<Int, LinkedList<TdApi.InputMessageContent>>(Launcher.database) {

        override fun read(id: Int): LinkedList<TdApi.InputMessageContent>? {

            return select { botId eq id }.firstOrNull()?.let { it[messages] }

        }

        override fun write(id: Int, value: LinkedList<TdApi.InputMessageContent>) {

            upsert(botId) {

                it[botId] = id
                it[messages] = value

            }

        }

        override fun delete(id: Int) {

            deleteWhere { botId eq id }

        }

    }

}