package io.nekohasekai.pm.database

import io.nekohasekai.ktlib.db.DatabaseCacheMap
import io.nekohasekai.ktlib.db.kryo
import io.nekohasekai.pm.Launcher
import org.jetbrains.exposed.sql.*
import td.TdApi
import java.util.*

object BotCommands : Table("bot_commands") {

    val botId = integer("bot_id").index()
    val command = text("command")
    val description = text("description")
    val hide = bool("hide").default(false)
    val messages = kryo<LinkedList<TdApi.InputMessageContent>>("messages")

    override val primaryKey = PrimaryKey(botId, command)

    object Cache : DatabaseCacheMap<Pair<Int, String>, BotCommand>(Launcher.database) {

        override fun read(id: Pair<Int, String>): BotCommand? {

            return BotCommands.select { botId eq id.first and (command eq id.second) }.firstOrNull()?.let { BotCommand(it) }

        }

        override fun write(id: Pair<Int, String>, value: BotCommand) {

            delete(id)

            insert {

                it[botId] = id.first
                it[command] = id.second
                it[description] = value.description
                it[hide] = value.hide
                it[messages] = value.messages

            }

        }

        override fun delete(id: Pair<Int, String>) {

            deleteWhere { botId eq id.first and (command eq id.second) }

        }

    }

}