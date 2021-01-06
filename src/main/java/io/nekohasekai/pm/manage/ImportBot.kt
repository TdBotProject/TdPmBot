package io.nekohasekai.pm.manage

import io.nekohasekai.ktlib.compress.xz
import io.nekohasekai.ktlib.core.byteBuffer
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.core.readFile
import io.nekohasekai.ktlib.core.readKryo
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.core.raw.deleteFile
import io.nekohasekai.ktlib.td.core.raw.getFileOrNull
import io.nekohasekai.ktlib.td.core.raw.getMessage
import io.nekohasekai.ktlib.td.core.raw.getMessageOrNull
import io.nekohasekai.ktlib.td.extensions.asByteArray
import io.nekohasekai.ktlib.td.extensions.asInt
import io.nekohasekai.ktlib.td.i18n.failed
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.*
import io.nekohasekai.pm.instance.BACKUP_VERSION
import io.nekohasekai.pm.manage.menu.BotMenu
import okhttp3.internal.closeQuietly
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import td.TdApi
import java.io.File

class ImportBot : TdHandler() {

    companion object {
        const val dataId = DATA_IMPORT_BOT
    }

    override fun onLoad() {
        initData(dataId)
    }

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        val document = (message.content as? TdApi.MessageDocument)?.takeIf {
            it.document.fileName.toLowerCase().endsWith(".td_pm")
        }?.document ?: return

        val L = localeFor(userId)

        if (!launcher.userAccessible(userId)) {
            if (!global.public) {
                sudo makeMd L.PRIVATE_INSTANCE.input(TdPmBot.repoUrl) syncTo chatId
                return
            } else if (document.document.size > 3 * 1024) {
                sudo makeMd L.IMPORT_TOO_LARGE.input(TdPmBot.repoUrl) syncTo chatId
                return
            }
        }

        sudo make Typing sendTo chatId
        val botFile = download(document.document)
        val input = botFile.inputStream().xz().byteBuffer()

        try {

            val version = input.readInt()

            if (version > BACKUP_VERSION) {
                sudo makeMd L.CLIENT_OUTDATE.input(TdPmBot.repoUrl) syncTo chatId
                return
            }

            val botId = input.readInt()
            val username = input.readString()

            input.closeQuietly()

            sudo makeMd L.IMPORT_INFO.input(botId, username) withMarkup inlineButton {
                dataLine(L.IMPORT_EXEC, dataId, document.document.id.asByteArray())
            } replyTo message

        } catch (e: Exception) {
            sudo makeMd L.INVALID_FILE.input(e.message ?: e.javaClass.simpleName) syncTo chatId
        }

        finishWithDelay()

    }

    override suspend fun onNewCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>
    ) {

        val document =
            (getMessageOrNull(chatId, getMessage(chatId, messageId).replyToMessageId)
                ?.content as? TdApi.MessageDocument)?.document?.document ?: getFileOrNull(data[0].asInt())

        if (document == null) {
            sudo makeAlert "Request timeout" answerTo chatId
            delete(chatId, messageId)
            return
        }

        val L = localeFor(userId)

        sudo make L.IMPORTING at messageId syncEditTo chatId
        sudo make Typing sendTo chatId

        val botFile = download(document)
        val input = botFile.inputStream().xz().byteBuffer()

        try {

            val version = input.readInt()

            if (version > BACKUP_VERSION) {
                sudo makeMd L.CLIENT_OUTDATE.input(TdPmBot.repoUrl) sendTo chatId
                return
            }

            val userAccessible = launcher.userAccessible(userId)

            var ignoreData = false

            val botId = input.readInt()
            val botUsername = input.readString()
            val botOwner = input.readInt()
            val botBotToken = input.readString()

            val existsBot = database { UserBot.findById(botId) }

            if (existsBot != null) {
                if (existsBot.owner != userId) {
                    sudo make L.failed { ALREADY_EXISTS } at messageId editTo messageId
                    sudo make CancelChatAction syncTo chatId
                    return
                } else {
                    sudo make L.IMPORT_EXISTS syncTo chatId
                    sudo make Typing sendTo chatId
                    ignoreData = true
                }
            } else if (botOwner != userId) {
                sudo make L.IMPORT_CHANGE_OWMNER syncTo chatId
                sudo make Typing sendTo chatId
                ignoreData = true
            }

            val userBot = existsBot ?: database {
                UserBot.new(botId) {
                    username = botUsername
                    owner = userId
                    botToken = botBotToken
                }
            }

            global.instanceMap.remove(botId)?.waitForClose()

            input.readFile(if (ignoreData) null else File(global.dataDir, "pm/$botId/td.binlog"))

            global.botCommands.filter { it.id.first == botId }.toList().forEach { global.botCommands.remove(it.id) }
            database.write {
                BotCommands.deleteWhere { BotCommands.botId eq botId }
            }

            val commands = input.readInt()
            if (!userAccessible && commands > 32) error("Too many commands")

            repeat(commands) {
                val command = BotCommand(
                    botId,
                    input.readString(),
                    input.readString(),
                    input.readBoolean(),
                    input.readBoolean(),
                    input.readKryo(),
                    input.readBoolean()
                )
                global.botCommands.fetch(botId to command.command).write(command)
            }

            if (input.readBoolean()) database.write {

                val integrationCache = global.botIntegrations.fetch(botId)
                val integration = integrationCache.value ?: BotIntegration.new(botId) {
                    integrationCache.value = this
                }

                integration.integration = input.readLong()
                integration.adminOnly = input.readBoolean()
                integration.paused = input.readBoolean()
                integration.flush()

            }

            if (input.readBoolean()) database.write {

                val settingCache = global.botSettings.fetch(botId)
                val setting = settingCache.value ?: database.write {
                    BotSetting.new(botId) {
                        settingCache.value = this
                    }
                }

                setting.keepActionMessages = input.readBoolean()
                setting.twoWaySync = input.readBoolean()
                setting.keepReply = input.readBoolean()
                setting.ignoreDeleteAction = input.readBoolean()
                setting.flush()

            }

            val startMessagesCache = global.startMessages.fetch(botId)
            startMessagesCache.set(if (input.readBoolean()) input.readKryo() else null)

            val userBlocks = input.readLong().toInt()
            if (!userAccessible && userBlocks > 512 || userBlocks > 10000) error("Too many blocks")
            database.write {
                repeat(userBlocks) {
                    UserBlocks.runCatching {
                        insert {
                            it[UserBlocks.botId] = botId
                            it[UserBlocks.blockedUser] = input.readInt()
                        }
                    }
                }
            }

            if (!ignoreData) {

                val messages = input.readLong().toInt()
                if (messages > 8092) error("Too many messages")

                repeat(messages) {
                    MessageRecords.runCatching {
                        insert {
                            it[MessageRecords.messageId] = input.readLong()
                            it[MessageRecords.type] = input.readInt()
                            it[MessageRecords.chatId] = input.readLong()
                            val targetId = input.readLong()
                            if (targetId != -1L) {
                                it[MessageRecords.targetId] = targetId
                            }
                            it[MessageRecords.createAt] = input.readInt()
                            it[MessageRecords.botId] = botId

                        }
                    }
                }

            }

            input.closeQuietly()

            sudo make L.IMPORTED sendTo chatId
            deleteFile(document.id)

            if (global.initBot(userBot).waitForAuth()) {
                findHandler<BotMenu>().botMenu(userId, chatId, 0L, false, botId, userBot)
            }

        } catch (e: Exception) {
            sudo makeMd L.INVALID_FILE.input(e.message ?: e.javaClass.simpleName) sendTo chatId
            clientLog.debug(e, "Import failed: ")
        }

    }

}