package io.github.nekohasekai.pm.instance

import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.pm.database.BotCommands
import io.github.nekohasekai.pm.database.MessageRecords
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

val TdHandler.messagesForCurrentBot get() = (MessageRecords.botId eq me.id)
val TdHandler.commandsForCurrentBot get() = (BotCommands.botId eq me.id)
