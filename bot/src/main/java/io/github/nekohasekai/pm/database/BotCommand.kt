package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.sql.ResultRow
import td.TdApi
import java.util.*

class BotCommand(

        var botId: Int,
        var command: String,
        var description: String,
        var hide: Boolean,
        var messages: LinkedList<TdApi.InputMessageContent>

) {

    constructor(row: ResultRow) : this(
            row[BotCommands.botId],
            row[BotCommands.command],
            row[BotCommands.description],
            row[BotCommands.hide],
            row[BotCommands.messages]
    )

}