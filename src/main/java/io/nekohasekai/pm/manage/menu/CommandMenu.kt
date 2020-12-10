package io.nekohasekai.pm.manage.menu

import cn.hutool.http.HtmlUtil
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.raw.editMessageReplyMarkup
import io.nekohasekai.ktlib.td.core.raw.editMessageReplyMarkupOrNull
import io.nekohasekai.ktlib.td.extensions.asByteArray
import io.nekohasekai.ktlib.td.extensions.asInputOrForward
import io.nekohasekai.ktlib.td.extensions.text
import io.nekohasekai.ktlib.td.extensions.userCalled
import io.nekohasekai.ktlib.td.i18n.*
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.BotCommand
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.instance.PmBot
import io.nekohasekai.pm.manage.BotHandler
import io.nekohasekai.pm.manage.MyBots
import td.TdApi
import java.util.*

class CommandMenu : BotHandler() {

    companion object {

        const val dataId = DATA_EDIT_COMMAND

        const val persistId = PERSIST_EDIT_FUNCTION

    }

    override fun onLoad() {

        if (sudo is TdPmBot) initData(dataId)

        initPersist(persistId)

    }

    fun commandButtons(
        L: LocaleController,
        botUserId: Int,
        userBot: UserBot?,
        command: BotCommand
    ): TdApi.ReplyMarkupInlineKeyboard {

        return inlineButton {

            val botId = botUserId.asByteArray()
            val commandName = command.command.encodeToByteArray()

            newLine {

                dataButton(L.COMMAND_RENAME, dataId, botId, commandName, byteArrayOf(0))
                dataButton(L.COMMAND_EDIT_DESCRIPTION, dataId, botId, commandName, byteArrayOf(1))

            }

            dataLine(L.COMMAND_EDIT_MESSAGES, dataId, botId, commandName, byteArrayOf(2))

            fun Boolean?.toBlock() = if (this == true) "■" else "□"

            newLine {

                textButton(L.COMMAND_HIDE)

                dataButton(command.hide.toBlock(), dataId, botId, commandName, byteArrayOf(3))

            }

            if (userBot == null && launcher.public) {

                newLine {

                    textButton(L.COMMAND_INPUT_WHEN_PUBLIC)

                    dataButton(command.inputWhenPublic.toBlock(), dataId, botId, commandName, byteArrayOf(4))

                }

            }

            newLine {

                dataButton(L.COMMAND_DELETE, dataId, botId, commandName, byteArrayOf(5))

            }

            dataLine(L.BACK_ARROW, CommandsMenu.dataId, botUserId.asByteArray())

        }

    }

    suspend fun commandMenu(
        botUserId: Int,
        userBot: UserBot?,
        command: BotCommand,
        userId: Int,
        chatId: Long,
        messageId: Long,
        isEdit: Boolean
    ) {

        val L = localeFor(userId)

        sudo makeMd L.COMMAND_HELP.input(
            botNameHtml(botUserId, userBot),
            botUserName(botUserId, userBot),
            command.command,
            HtmlUtil.escape(command.description),
            if (command.messages.isEmpty()) L.EMPTY else L.MESSAGES_STATUS_COUNT.input(command.messages.size),
            botUserName(botUserId, userBot),
            command.command,
            if (userBot == null && launcher.public) L.COMMAND_INPUT_WHEN_PUBLIC_DEF else ""
        ) withMarkup commandButtons(L, botUserId, userBot, command) onSuccess {

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
                command.botId,
                command.command,
                command.description,
                command.hide,
                LinkedList(),
                command.inputWhenPublic
            )

        }

        constructor() : this(0, null, 0L, null)

        var edited = false
    }


    override suspend fun onNewBotCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>,
        botUserId: Int,
        userBot: UserBot?
    ) {

        val command = launcher.botCommands.fetch(botUserId to data[0].decodeToString()).value ?: return

        if (data.size < 2) {

            sudo syncConfirmTo queryId

            commandMenu(botUserId, userBot, command, userId, chatId, messageId, true)

            return

        }

        val L = localeFor(userId)

        when (val action = data[1][0].toInt()) {

            -1 -> {

                sudo removePersist userId

                sudo confirmTo queryId

                commandMenu(botUserId, userBot, command, userId, chatId, messageId, true)

            }

            0 -> {

                writePersist(
                    userId,
                    persistId,
                    0,
                    RenameCache(botUserId, userBot, command, messageId, false),
                    allowFunction = true
                )

                sudo confirmTo queryId

                sudo make L.COMMAND_INPUT_NAME withMarkup inlineButton {

                    dataLine(
                        L.BACK_ARROW,
                        dataId,
                        botUserId.asByteArray(),
                        command.command.encodeToByteArray(),
                        byteArrayOf(-1)
                    )

                } at messageId editTo chatId

            }

            1 -> {

                writePersist(userId, persistId, 0, RenameCache(botUserId, userBot, command, messageId, true))

                sudo confirmTo queryId

                sudo make L.COMMAND_INPUT_DESCRIPTION withMarkup inlineButton {

                    dataLine(
                        L.BACK_ARROW,
                        dataId,
                        botUserId.asByteArray(),
                        command.command.encodeToByteArray(),
                        byteArrayOf(-1)
                    )

                } at messageId editTo chatId

            }

            2 -> {

                val self = if (userBot != null) launcher.initBot(userBot) else this

                self.writePersist(
                    userId,
                    persistId,
                    1,
                    EditMessagesCache(botUserId, userBot, messageId, command),
                    allowFunction = true
                )

                if (userBot != null) {

                    self make L.INPUT_MESSAGES sendTo chatId

                    sudo make L.JUMP_TO_SET.input(userBot.username) withMarkup inlineButton {

                        dataLine(L.BACK_ARROW, dataId, botUserId.asByteArray(), command.command.encodeToByteArray())

                    } at messageId editTo chatId

                } else {

                    sudo make L.INPUT_MESSAGES withMarkup inlineButton {

                        dataLine(
                            L.BACK_ARROW,
                            dataId,
                            botUserId.asByteArray(),
                            command.command.encodeToByteArray(),
                            byteArrayOf(-1)
                        )

                    } at messageId editTo chatId

                }

            }

            3, 4 -> {

                val target: Boolean

                launcher.botCommands.fetch(botUserId to command.command).apply {

                    val currVal = value

                    if (currVal == null) {

                        sudo confirmTo queryId

                        editMessageReplyMarkup(chatId, messageId, commandButtons(L, botUserId, userBot, command))

                        return

                    }

                    when (action) {

                        3 -> {

                            target = !currVal.hide

                            currVal.hide = target

                        }

                        //4 -> {

                        else -> {

                            target = !currVal.inputWhenPublic

                            currVal.inputWhenPublic = target

                        }

                    }

                    changed = true

                    flush()

                }

                sudo makeAnswer (if (!target) L.DISABLED else L.ENABLED) answerTo queryId

                editMessageReplyMarkup(chatId, messageId, commandButtons(L, botUserId, userBot, command))

                if (userBot != null) launcher.initBot(userBot).updateCommands() else (sudo as TdPmBot).updateCommands()

            }

            5 -> {

                launcher.botCommands.fetch(botUserId to command.command).apply {

                    if (value != null) {

                        value = null
                        changed = true

                        flush()

                    }

                }

                sudo makeAnswer L.DELETED answerTo queryId

                findHandler<CommandsMenu>().commandsMenu(botUserId, userBot, userId, chatId, messageId, true)

                if (userBot != null) launcher.initBot(userBot).updateCommands() else (sudo as TdPmBot).updateCommands()

            }

        }

    }

    override suspend fun onPersistMessage(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        subId: Int,
        data: Array<Any?>
    ) {

        val L = localeFor(userId)

        if (subId == 0) {

            val cache = data[0] as RenameCache

            suspend fun removeBack() {

                if (!cache.edited) {

                    editMessageReplyMarkupOrNull(chatId, cache.startsAt, null)

                    cache.edited = true

                }

            }

            val text = message.text

            if (!cache.description) {

                if (text == null || !text.matches("[a-z][a-z0-9_]{0,31}".toRegex())) {

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

                launcher.botCommands.fetch(cache.botUserId to cache.command.command).apply {

                    value = null
                    changed = true

                    flush()

                }

                cache.command.command = text

                launcher.botCommands.fetch(cache.botUserId to cache.command.command).apply {

                    value = cache.command
                    changed = true

                    flush()

                }

            } else {

                cache.command.description = text

                launcher.botCommands.fetch(cache.botUserId to cache.command.command).apply {

                    value = cache.command
                    changed = true

                    flush()

                }

            }

            sudo removePersist userId

            removeBack()

            sudo make L.SETTING_SAVED sendTo chatId

            commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)

        } else if (subId == 1) {

            val cache = data[0] as EditMessagesCache

            if (!cache.edited) {

                launcher.editMessageReplyMarkupOrNull(chatId, cache.startsAt, null)

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

    override suspend fun onPersistFunction(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        subId: Int,
        data: Array<Any?>,
        function: String,
        param: String,
        params: Array<String>,
        originParams: Array<String>
    ) {

        if (subId != 1) rejectFunction()

        val cache = data[0] as EditMessagesCache

        if (!cache.edited) {

            editMessageReplyMarkupOrNull(chatId, cache.startsAt, null)

            cache.edited = true

        }

        val L = localeFor(userId)

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

            if (chatId != launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                sudo removePersist userId

                onSendTimeoutMessage(userId, chatId)

                return

            }

            userCalled(userId, "submitted messages to function /${cache.command} for $botUserId")

            launcher.botCommands.fetch(cache.botUserId to cache.command.command).apply {

                value = cache.command
                changed = true

                flush()

            }

            sudo make L.SETTING_SAVED sendTo chatId

            (sudo as? TdPmBot)?.updateCommands()
            (sudo as? PmBot)?.updateCommands()

            (if (cache.userBot != null) launcher.findHandler() else this)
                .commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)


        } else {

            rejectFunction()

        }

    }

    override suspend fun onPersistCancel(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        subId: Int,
        data: Array<Any?>
    ) {

        if (subId == 0) {

            val cache = data[0] as RenameCache

            if (!cache.edited) {

                editMessageReplyMarkupOrNull(chatId, cache.startsAt, null)

                cache.edited = true

            }

            commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)

        } else if (subId == 1) {

            val cache = data[0] as EditMessagesCache

            if (!cache.edited) {

                editMessageReplyMarkupOrNull(chatId, cache.startsAt, null)

                cache.edited = true

            }

            commandMenu(cache.botUserId, cache.userBot, cache.command, userId, chatId, 0L, false)

        }

    }

}