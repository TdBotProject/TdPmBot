package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.pm.database.BotCommands
import io.nekohasekai.pm.database.MessageRecords
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

val TdHandler.messagesForCurrentBot get() = (MessageRecords.botId eq me.id)
val TdHandler.commandsForCurrentBot get() = (BotCommands.botId eq me.id)
val TdHandler.userBot get() = (sudo as? PmBot)?.userBot