package io.nekohasekai.pm.database

import com.esotericsoftware.kryo.KryoException
import io.nekohasekai.ktlib.db.DatabaseCacheMap
import io.nekohasekai.ktlib.db.DatabaseDispatcher
import io.nekohasekai.ktlib.db.kryoAny
import io.nekohasekai.ktlib.db.upsert
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import td.TdApi
import java.util.*

object StartMessages : Table("pm_start_messages") {

    val botId = integer("bot_id").uniqueIndex()

    val messages = kryoAny<LinkedList<TdApi.InputMessageContent>>("messages")

    class Cache(database: DatabaseDispatcher) : DatabaseCacheMap<Int, LinkedList<TdApi.InputMessageContent>>(database) {

        override fun read(id: Int): LinkedList<TdApi.InputMessageContent>? {

            return try {
                select { botId eq id }.firstOrNull()?.let { it[messages] }
            } catch (e: KryoException) {
                delete(id)
                null
            }


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