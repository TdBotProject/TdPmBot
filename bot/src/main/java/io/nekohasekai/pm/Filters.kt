package io.nekohasekai.pm

import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.pm.database.BotCommands
import io.nekohasekai.pm.database.MessageRecords
import io.nekohasekai.pm.instance.PmBot
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

val TdHandler.messagesForCurrentBot get() = (MessageRecords.botId eq me.id)
val TdHandler.commandsForCurrentBot get() = (BotCommands.botId eq me.id)
val TdHandler.userBot get() = (sudo as? PmBot)?.userBot

val TdHandler.launcher
    get() = when (val sudo = sudo) {
        is TdPmBot -> sudo
        is PmBot -> sudo.launcher
        else -> error("invalid handler")
    }