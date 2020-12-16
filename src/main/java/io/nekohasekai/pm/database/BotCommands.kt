package io.nekohasekai.pm.database

import io.nekohasekai.ktlib.db.DatabaseCacheMap
import io.nekohasekai.ktlib.db.DatabaseDispatcher
import io.nekohasekai.ktlib.db.kryo
import org.jetbrains.exposed.sql.*
import td.TdApi
import java.util.*

object BotCommands : Table("bot_commands") {

    val botId = integer("bot_id").index()

    val command = text("command")
    val description = text("description")
    val hide = bool("hide").default(false)
    val disable = bool("disable").default(false)
    val messages = kryo<LinkedList<TdApi.InputMessageContent>>("messages")
    val inputWhenPublic = bool("input").default(false)

    override val primaryKey = PrimaryKey(botId, command)

    class Cache(database: DatabaseDispatcher) : DatabaseCacheMap<Pair<Int, String>, BotCommand>(database) {

        override fun read(id: Pair<Int, String>): BotCommand? {

            return BotCommands.select { botId eq id.first and (command eq id.second) }.firstOrNull()
                ?.let { BotCommand(it) }

        }

        override fun write(id: Pair<Int, String>, value: BotCommand) {

            delete(id)

            insert {

                it[botId] = id.first
                it[command] = id.second
                it[description] = value.description
                it[hide] = value.hide
                it[disable] = value.disable
                it[messages] = value.messages
                it[inputWhenPublic] = value.inputWhenPublic

            }

        }

        override fun delete(id: Pair<Int, String>) {

            deleteWhere { botId eq id.first and (command eq id.second) }

        }

    }

}