package io.nekohasekai.pm.instance

import cn.hutool.core.io.FileUtil
import io.nekohasekai.ktlib.compress.xz
import io.nekohasekai.ktlib.core.byteBuffer
import io.nekohasekai.ktlib.core.writeFile
import io.nekohasekai.ktlib.core.writeKryo
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.pm.commandsForCurrentBot
import io.nekohasekai.pm.database.BotCommand
import io.nekohasekai.pm.database.BotCommands
import io.nekohasekai.pm.database.MessageRecords
import io.nekohasekai.pm.database.UserBlocks
import io.nekohasekai.pm.manage.global
import okhttp3.internal.closeQuietly
import org.jetbrains.exposed.sql.select
import java.io.File

const val BACKUP_VERSION = 1

fun TdHandler.backupToFile(bot: PmBot): File {

    val cacheFile = File(global.cacheDir, bot.userBot.username + ".td_pm")

    val output = FileUtil.touch(cacheFile).outputStream().xz().byteBuffer()

    output.writeInt(BACKUP_VERSION)

    output.writeInt(bot.userBot.botId)
    output.writeString(bot.userBot.username)
    output.writeInt(bot.userBot.owner)
    output.writeString(bot.userBot.botToken)

    output.writeFile(File(bot.options.databaseDirectory, "td.binlog"))

    val commands = database { BotCommands.select { bot.commandsForCurrentBot }.map { BotCommand(it) } }
    output.writeInt(commands.size)
    for (command in commands) {
        output.writeString(command.command)
        output.writeString(command.description)
        output.writeBoolean(command.hide)
        output.writeBoolean(command.disable)
        output.writeKryo(command.messages)
        output.writeBoolean(command.inputWhenPublic)
    }

    val integration = bot.integration
    output.writeBoolean(integration != null)
    if (integration != null) {
        output.writeLong(integration.integration)
        output.writeBoolean(integration.adminOnly)
        output.writeBoolean(integration.paused)
    }

    val settings = bot.settings
    output.writeBoolean(settings != null)
    if (settings != null) {
        output.writeBoolean(settings.keepActionMessages)
        output.writeBoolean(settings.twoWaySync)
        output.writeBoolean(settings.keepReply)
        output.writeBoolean(settings.ignoreDeleteAction)
    }

    val startMessages = global.startMessages.fetch(bot.userBot.botId).value
    output.writeBoolean(startMessages != null)
    if (startMessages != null) {
        output.writeKryo(startMessages)
    }

    database {
        val userBlocks = UserBlocks.select { UserBlocks.botId eq bot.userBot.botId }
        output.writeLong(userBlocks.count())
        for (userBlock in userBlocks) {
            output.writeInt(userBlock[UserBlocks.blockedUser])
        }

        val messageRecords = MessageRecords.select { MessageRecords.botId eq bot.userBot.botId }
        output.writeLong(messageRecords.count())
        for (messageRecord in messageRecords) {
            output.writeLong(messageRecord[MessageRecords.messageId])
            output.writeInt(messageRecord[MessageRecords.type])
            output.writeLong(messageRecord[MessageRecords.chatId])
            output.writeLong(messageRecord[MessageRecords.targetId] ?: -1)
            output.writeInt(messageRecord[MessageRecords.createAt])
        }
    }

    output.flush()
    output.closeQuietly()

    return cacheFile

}