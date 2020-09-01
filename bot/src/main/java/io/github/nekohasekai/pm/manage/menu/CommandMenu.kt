package io.github.nekohasekai.pm.manage.menu

import cn.hutool.http.HtmlUtil
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.raw.editMessageReplyMarkup
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.*
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.BotCommand
import io.github.nekohasekai.pm.database.BotCommands
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.instance.BotInstances
import io.github.nekohasekai.pm.instance.PmBot
import io.github.nekohasekai.pm.manage.BotHandler
import io.github.nekohasekai.pm.manage.MyBots
import td.TdApi
import java.util.*

class CommandMenu : BotHandler() {

    companion object {

        const val dataId = DATA_EDIT_COMMAND

        const val persistId = PERSIST_EDIT_FUNCTION

    }

    override fun onLoad() {

        if (sudo is Launcher) initData(dataId)

        initPersist(persistId)

    }

    suspend fun commandMenu(botUserId: Int, userBot: UserBot?, command: BotCommand, userId: Int, chatId: Long, messageId: Long, isEdit: Boolean) {

        val L = LocaleController.forChat(userId)

        sudo makeHtml L.COMMAND_HELP.input(
                botNameHtml(botUserId, userBot),
                botUserName(botUserId, userBot),
                command.command,
                HtmlUtil.escape(command.description),
                if (command.messages.isEmpty()) L.EMPTY else L.MESSAGES_STATUS_COUNT.input(command.messages.size),
                if (command.hide) L.ENABLED else L.DISABLED,
                botUserName(botUserId, userBot),
                command.command
        ) withMarkup inlineButton {

            val botId = botUserId.toByteArray()
            val commandName = command.command.encodeToByteArray()

            newLine {

                dataButton(L.COMMAND_RENAME, dataId, botId, commandName, byteArrayOf(0))
                dataButton(L.COMMAND_EDIT_DESCRIPTION, dataId, botId, commandName, byteArrayOf(1))

            }

            dataLine(L.COMMAND_EDIT_MESSAGES, dataId, botId, commandName, byteArrayOf(2))

            newLine {

                if (!command.hide) {

                    dataButton(L.COMMAND_HIDE, dataId, botId, commandName, byteArrayOf(3))

                } else {

                    dataButton(L.COMMAND_SHOW, dataId, botId, commandName, byteArrayOf(4))

                }

                dataButton(L.COMMAND_DELETE, dataId, botId, commandName, byteArrayOf(5))

            }

            dataLine(L.BACK_ARROW, CommandsMenu.dataId, botUserId.toByteArray())

        } onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

    }

    class RenameCache(
            var botUserId: Int,
            var userBot: UserBot?,
            command: BotCommand?,
            var startsAt: Long,
            var description: Boolean
    ) {

        lateinit var command: BotCommand

        init {

            if (command != null) {

                this.command = command

            }

        }

        constructor() : this(0, null, null, 0L, false)

        var edited = false

    }

    class EditMessagesCache(
            var botUserId: Int,
            var userBot: UserBot?,
            var startsAt: Long,
            command: BotCommand?
    ) {

        lateinit var command: BotCommand

        init {

            if (command != null) this.command = BotCommand(
                    command.botId, command.command, command.description, command.hide, LinkedList()
            )

        }

        constructor() : this(0, null, 0L, null)

        var edited = false
    }


    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        val command = BotCommands.Cache.fetch(botUserId to data[0].decodeToString()).value ?: return

        if (data.size < 2) {

            sudo confirmTo queryId

            commandMenu(botUserId, userBot, command, userId, chatId, messageId, true)

            return

        }

        val L = LocaleController.forChat(userId)

        when (data[1][0].toInt()) {

            -1 -> {

                sudo removePersist userId

                sudo confirmTo queryId

                commandMenu(botUserId, userBot, command, userId, chatId, messageId, true)

            }

            0 -> {

                writePersist(userId, persistId, 0, RenameCache(botUserId, userBot, command, messageId, false), allowFunction = true)

                sudo confirmTo queryId

                sudo make L.COMMAND_INPUT_NAME withMarkup inlineButton {

                    dataLine(L.BACK_ARROW, dataId, botUserId.toByteArray(), command.command.encodeToByteArray(), byteArrayOf(-1))

                } at messageId editTo chatId

            }

            1 -> {

                writePersist(userId, persistId, 0, RenameCache(botUserId, userBot, command, messageId, true))

                sudo confirmTo queryId

                sudo make L.COMMAND_INPUT_DESCRIPTION withMarkup inlineButton {

                    dataLine(L.BACK_ARROW, dataId, botUserId.toByteArray(), command.command.encodeToByteArray(), byteArrayOf(-1))

                } at messageId editTo chatId

            }

            2 -> {

                val self = if (userBot != null) BotInstances.initBot(userBot) else this

                self.writePersist(userId, persistId, 1L, EditMessagesCache(botUserId, userBot, messageId, command), allowFunction = true)

                if (userBot != null) {

                    self make L.INPUT_MESSAGES sendTo chatId

                    sudo make L.JUMP_TO_SET.input(userBot.username) withMarkup inlineButton {

                        dataLine(L.BACK_ARROW, dataId, botUserId.toByteArray(), command.command.encodeToByteArray())

                    } at messageId editTo chatId

                } else {

                    sudo make L.INPUT_MESSAGES withMarkup inlineButton {

                        dataLine(L.BACK_ARROW, dataId, botUserId.toByteArray(), command.command.encodeToByteArray(), byteArrayOf(-1))

                    } at messageId editTo chatId

                }

            }

            3 -> {

                BotCommands.Cache.fetch(botUserId to command.command).apply {

                    if (value == null) {

                        sudo confirmTo queryId

                        findHandler<CommandsMenu>().commandsMenu(botUserId, userBot, userId, chatId, messageId, true)

                        return

                    }

                    if (!value!!.hide) {

                        value!!.hide = true
                        changed = true

                        flush()

                    }

                }

                sudo makeAnswer L.ENABLED answerTo queryId

                commandMenu(botUserId, userBot, command, userId, chatId, messageId, true)

                if (userBot != null) BotInstances.initBot(userBot).updateCommands() else (sudo as Launcher).updateCommands()

            }

            4 -> {

                BotCommands.Cache.fetch(botUserId to command.command).apply {

                    if (value == null) {

                        sudo confirmTo queryId

                        findHandler<CommandsMenu>().commandsMenu(botUserId, userBot, userId, chatId, messageId, true)

                        return

                    }

                    if (value!!.hide) {

                        value!!.hide = false
                        changed = true

                        flush()

                    }


                }

                sudo makeAnswer L.DISABLED answerTo queryId

                commandMenu(botUserId, userBot, command, userId, chatId, messageId, true)

                if (userBot != null) BotInstances.initBot(userBot).updateCommands() else (sudo as Launcher).updateCommands()

            }

            5 -> {

                BotCommands.Cache.fetch(botUserId to command.command).apply {

                    if (value != null) {

                        value = null
                        changed = true

                        flush()

                    }

                }

                sudo makeAnswer L.DELETED answerTo queryId

                findHandler<CommandsMenu>().commandsMenu(botUserId, userBot, userId, chatId, messageId, true)

                if (userBot != null) BotInstances.initBot(userBot).updateCommands() else (sudo as Launcher).updateCommands()

            }

        }

    }

    override suspend fun onPersistMessage(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any?>) {

        val L = LocaleController.forChat(userId)

        if (subId == 0L) {

            val cache = data[0] as RenameCache

            suspend fun removeBack() {

                if (!cache.edited) {

                    editMessageReplyMarkup(chatId, cache.startsAt, null)

                    cache.edited = true

                }

            }

            val text = message.text

            if (!cache.description) {

                if (text == null || !text.contains("[a-z0-9_]{1,32}".toRegex())) {

                    removeBack()

                    sudo make L.COMMAND_NAME_INVALID.input(
                            L.CANCEL_NOTIFICATION
                    ) replyTo message

                    return

                }

            } else {

                if (text == null || text.length !in 3..256) {

                    removeBack()

                    sudo make L.COMMAND_DESCRIPTION_INVALID.input(
                            L.CANCEL_NOTIFICATION
                    ) replyTo message

                    return

                }


            }

            if (!cache.description && text == "start") {

                sudo makeHtml L.COMMAND_START replyTo message

                return

            }

            if (!cache.description) {

                BotCommands.Cache.fetch(cache.botUserId to cache.command.command).apply {

                    value = null
                    changed = true

                    flush()

                }

                cache.command.command = text

                BotCommands.Cache.fetch(cache.botUserId to cache.command.command).apply {

                    value = cache.command
                    changed = true

                    flush()

                }

            } else {

                cache.command.description = text

                BotCommands.Cache.fetch(cache.botUserId to cache.command.command).apply {

                    value = cache.command
                    changed = true

                    flush()

                }

            }

            sudo removePersist userId

            removeBack()

            sudo make L.SETTING_SAVED sendTo chatId

            commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)

        } else if (subId == 1L) {

            val cache = data[0] as EditMessagesCache

            if (!cache.edited) {

                Launcher.editMessageReplyMarkup(chatId, cache.startsAt, null)

                cache.edited = true

            }

            val content = message.asInputOrForward

            cache.command.messages.add(content)

            if (content is TdApi.InputMessageForwarded) {

                sudo make L.MESSAGE_ADDED_FWD sendTo chatId

            } else {

                sudo make L.MESSAGE_ADDED sendTo chatId

            }

        }

    }

    override suspend fun onPersistFunction(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any?>, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (subId != 1L) rejectFunction()

        val cache = data[0] as EditMessagesCache

        if (!cache.edited) {

            editMessageReplyMarkup(chatId, cache.startsAt, null)

            cache.edited = true

        }

        val L = LocaleController.forChat(userId)

        if (function == "preview") {

            if (cache.command.messages.isEmpty()) {

                sudo make L.EMPTY sendTo chatId

            } else {

                try {

                    cache.command.messages.forEach {

                        sudo make it syncTo chatId

                    }

                } catch (e: TdException) {

                    sudo make L.ERROR_IN_PREVIEW sendTo chatId

                }

            }

        } else if (function == "submit") {

            sudo removePersist userId

            val botUserId = cache.botUserId

            if (chatId != Launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                sudo removePersist userId

                onSendTimeoutMessage(userId, chatId)

                return

            }

            userCalled(userId, "submitted messages to function /${cache.command} for $botUserId")

            BotCommands.Cache.fetch(cache.botUserId to cache.command.command).apply {

                value = cache.command
                changed = true

                flush()

            }

            sudo make L.SETTING_SAVED sendTo chatId

            (sudo as? Launcher)?.updateCommands()
            (sudo as? PmBot)?.updateCommands()

            (if (cache.userBot != null) Launcher.findHandler() else this)
                    .commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)


        } else {

            rejectFunction()

        }

    }

    override suspend fun onPersistCancel(userId: Int, chatId: Long, message: TdApi.Message, subId: Long, data: Array<Any?>) {

        if (subId == 0L) {

            val cache = data[0] as RenameCache

            if (!cache.edited) {

                editMessageReplyMarkup(chatId, cache.startsAt, null)

                cache.edited = true

            }

            commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)

        } else if (subId == 1L) {

            val cache = data[0] as EditMessagesCache

            if (!cache.edited) {

                editMessageReplyMarkup(chatId, cache.startsAt, null)

                cache.edited = true

            }

            commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)

        }

    }

}