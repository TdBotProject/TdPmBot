package io.nekohasekai.pm.database

import io.nekohasekai.ktlib.db.*
import org.jetbrains.exposed.sql.*
import td.TdApi
import java.util.*

object StartMessages : Table("pm_start_messages") {

    val botId = integer("bot_id").uniqueIndex()

    val messages = kryo<LinkedList<TdApi.InputMessageContent>>("messages")

    class Cache(database: DatabaseDispatcher) : DatabaseCacheMap<Int, LinkedList<TdApi.InputMessageContent>>(database) {

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