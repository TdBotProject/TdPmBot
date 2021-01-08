package io.nekohasekai.pm.instance

import cn.hutool.core.io.FileUtil
import io.nekohasekai.ktlib.compress.tar
import io.nekohasekai.ktlib.compress.writeDirectory
import io.nekohasekai.ktlib.compress.writeFile
import io.nekohasekai.ktlib.compress.xz
import io.nekohasekai.ktlib.core.byteBuffer
import io.nekohasekai.ktlib.core.writeFile
import io.nekohasekai.ktlib.core.writeKryo
import io.nekohasekai.ktlib.db.forceCreateTables
import io.nekohasekai.ktlib.db.openSqliteDatabase
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.pm.commandsForCurrentBot
import io.nekohasekai.pm.database.*
import io.nekohasekai.pm.manage.global
import okhttp3.internal.closeQuietly
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.io.File

const val BACKUP_VERSION = 1

fun TdHandler.backupToFile(bot: PmBot): File {

    val cacheFile = File(global.cacheDir, bot.userBot.username + ".td_pm")

    val output = FileUtil.touch(cacheFile).outputStream().xz().byteBuffer()

    output.writeInt(BACKUP_VERSION)

    output.writeInt(bot.botUserId)
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

    val startMessages = global.startMessages.fetch(bot.botUserId).value
    output.writeBoolean(startMessages != null)
    if (startMessages != null) {
        output.writeKryo(startMessages)
    }

    database {
        val userBlocks = UserBlocks.select { UserBlocks.botId eq bot.botUserId }
        output.writeLong(userBlocks.count())
        for (userBlock in userBlocks) {
            output.writeInt(userBlock[UserBlocks.blockedUser])
        }

        val messageRecords = MessageRecords.select { MessageRecords.botId eq bot.botUserId }
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


fun TdHandler.backupToDir(bot: PmBot): File {

    val cacheFile = File(global.cacheDir, bot.userBot.username + ".tar.xz")

    val output = FileUtil.touch(cacheFile).outputStream().xz().tar()

    output.writeFile(
        "pm.yml", """
BOT_TOKEN: ${bot.userBot.botToken}
BOT_LANG: ${localeFor(bot.userBot.owner).LANG}
B0T_OWNER: ${bot.userBot.owner}
PM_MODE: private
PM_WHITE_LIST:
LOG_LEVEL: INFO
DATA_DIR: data
CACHE_DIR: cache
ERROR_REPORT: group
BACKUP_OVERWRITE: -1
AUTO_BACKUP: disable
"""
    )

    output.writeDirectory("data/")

    output.writeFile("data/td.binlog", File(bot.options.databaseDirectory, "td.binlog"))

    output.flush()

    val databaseCache = File(global.cacheDir, bot.userBot.username + "_pm_data.db")

    val dbToSave = openSqliteDatabase(databaseCache)

    dbToSave.write {

        forceCreateTables(
            StartMessages,
            BotIntegrations,
            BotSettings,
            BotCommands,
            MessageRecords,
            UserBlocks
        )

    }

    val startMessages = StartMessages.Cache(dbToSave)
    val botCommands = BotCommands.Cache(dbToSave)

    val integration = bot.integration
    if (integration != null) dbToSave.write {
        BotIntegration.new(bot.botUserId) {
            this.integration = integration.integration
            this.adminOnly = integration.adminOnly
            this.paused = integration.paused
        }
    }

    val settings = bot.settings
    if (settings != null) dbToSave.write {
        BotSetting.new(bot.botUserId) {
            this.keepActionMessages = settings.keepActionMessages
            this.twoWaySync = settings.twoWaySync
            this.keepReply = settings.keepReply
            this.ignoreDeleteAction = settings.ignoreDeleteAction
        }
    }

    val starts = global.startMessages.fetch(bot.botUserId).value
    if (starts != null) dbToSave.write {
        startMessages.write(bot.botUserId, starts)
    }

    database {
        for (row in BotCommands.select { bot.commandsForCurrentBot }) {
            val command = BotCommand(row)
            botCommands.write(bot.botUserId to command.command, command)
        }

        for (row in MessageRecords.select { MessageRecords.botId eq bot.botUserId }) {
            val messageId = row[MessageRecords.messageId]
            val type = row[MessageRecords.type]
            val chatId = row[MessageRecords.chatId]
            val targetId = row[MessageRecords.targetId]
            val createAt = row[MessageRecords.createAt]
            dbToSave.write {
                MessageRecords.insert {
                    it[MessageRecords.messageId] = messageId
                    it[MessageRecords.type] = type
                    it[MessageRecords.chatId] = chatId
                    it[MessageRecords.targetId] = targetId
                    it[MessageRecords.createAt] = createAt
                    it[botId] = bot.botUserId
                }
            }
        }
        dbToSave.write {
            flushCache()
            close()
        }
    }

    output.writeFile("data/pm_data.db", databaseCache)

    databaseCache.delete()

    output.flush()
    output.close()

    return cacheFile

}