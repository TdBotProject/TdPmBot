package io.nekohasekai.pm.manage.menu

import cn.hutool.http.HtmlUtil
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.TdException
import io.nekohasekai.ktlib.td.core.raw.editMessageReplyMarkupOrNull
import io.nekohasekai.ktlib.td.extensions.*
import io.nekohasekai.ktlib.td.i18n.*
import io.nekohasekai.ktlib.td.utils.confirmTo
import io.nekohasekai.ktlib.td.utils.inlineButton
import io.nekohasekai.ktlib.td.utils.make
import io.nekohasekai.ktlib.td.utils.makeHtml
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.BotCommand
import io.nekohasekai.pm.database.BotCommands
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.instance.PmBot
import io.nekohasekai.pm.manage.BotHandler
import io.nekohasekai.pm.manage.MyBots
import org.jetbrains.exposed.sql.select
import td.TdApi
import java.util.*

class CommandsMenu : BotHandler() {

    companion object {

        const val dataId = DATA_EDIT_COMMANDS

        const val persistId = PERSIST_NEW_FUNCTION

    }

    override fun onLoad() {

        if (sudo is TdPmBot) initData(dataId)

        initPersist(persistId)

        sudo addHandler CommandMenu()

    }

    suspend fun commandsMenu(
        botUserId: Int,
        userBot: UserBot?,
        userId: Int,
        chatId: Long,
        messageId: Long,
        isEdit: Boolean
    ) {

        val commands = database { BotCommands.select { BotCommands.botId eq botUserId }.toList() }

        val L = localeFor(userId)

        sudo makeHtml L.COMMANDS_HELP.input(
            botNameHtml(botUserId, userBot),
            botUserName(botUserId, userBot),
            if (commands.isEmpty()) L.EMPTY else
                commands.joinToString("\n") { row ->
                    ("/" + row[BotCommands.command] + " - " + HtmlUtil.escape(row[BotCommands.description])).let {
                        if (row[BotCommands.hide]) it.htmlDelete else it.htmlCode
                    }
                }
        ) withMarkup inlineButton {

            dataLine(L.COMMAND_NEW, dataId, botUserId.asByteArray(), byteArrayOf(0))

            commands.forEach {

                dataLine(
                    "/" + it[BotCommands.command],
                    CommandMenu.dataId,
                    botUserId.asByteArray(),
                    it[BotCommands.command].encodeToByteArray()
                )

            }

            dataLine(L.BACK_ARROW, BotMenu.dataId, botUserId.asByteArray())

        } onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

    }

    class CreateBotCommandCache(
        var botId: Int,
        var startsAt: Long
    ) {

        constructor() : this(0, 0L)

        var edited = false
        var step = 0
        lateinit var command: String
        lateinit var description: String
        var messages = LinkedList<TdApi.InputMessageContent>()

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

        if (data.isEmpty()) {

            sudo confirmTo queryId

            commandsMenu(botUserId, userBot, userId, chatId, messageId, true)

            return

        }

        val L = localeFor(userId)

        when (data[0][0].toInt()) {

            -1 -> {

                sudo removePersist userId

                sudo confirmTo queryId

                commandsMenu(botUserId, userBot, userId, chatId, messageId, true)


            }

            0 -> {

                writePersist(
                    userId,
                    persistId,
                    0,
                    CreateBotCommandCache(botUserId, messageId),
                    allowFunction = true
                )

                sudo make L.COMMAND_INPUT_NAME at messageId withMarkup inlineButton {

                    dataLine(L.BACK_ARROW, dataId, botUserId.asByteArray(), byteArrayOf(-1))

                } editTo chatId

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

        val cache = data[0] as CreateBotCommandCache

        val L = localeFor(userId)

        if (function == "previous") {

            if (cache.step == 1) {

                cache.step = 0

                sudo make L.COMMAND_INPUT_NAME sendTo chatId

            } else rejectFunction()

        } else if (function == "preview") {

            if (cache.messages.isEmpty()) {

                sudo make L.EMPTY sendTo chatId

            } else {

                try {

                    cache.messages.forEach {

                        sudo make it syncTo chatId

                    }

                } catch (e: TdException) {

                    sudo make L.ERROR_IN_PREVIEW sendTo chatId

                }

            }

        } else if (function == "submit") {

            sudo removePersist userId

            val botUserId = cache.botId

            if (chatId != launcher.admin && database { UserBot.findById(botUserId)?.owner != userId }) {

                // 权限检查

                sudo removePersist userId

                onSendTimeoutMessage(userId, chatId)

                return

            }

            userCalled(userId, "submitted messages to function /${cache.command} for $botUserId")

            launcher.botCommands.fetch(botUserId to cache.command).apply {

                value = BotCommand(cache.botId, cache.command, cache.description, false, cache.messages, false)
                changed = true

                flush()

            }

            sudo make L.SETTING_SAVED sendTo chatId

            (sudo as? TdPmBot)?.updateCommands()
            (sudo as? PmBot)?.updateCommands()

            launcher.findHandler<CommandsMenu>()
                .commandsMenu(cache.botId, findUserBot(cache.botId), userId, chatId, 0L, false)

        } else {

            rejectFunction()

        }

    }

    override suspend fun onPersistMessage(
        userId: Int,
        chatId: Long,
        message: TdApi.Message,
        subId: Int,
        data: Array<Any?>
    ) {

        val cache = data[0] as CreateBotCommandCache

        suspend fun removeBack() {

            if (!cache.edited) {

                editMessageReplyMarkupOrNull(chatId, cache.startsAt, null)

                cache.edited = true

            }

        }

        val L = localeFor(userId)

        when (cache.step) {

            0 -> {

                val command = message.text

                if (command == null || !command.contains("[a-z][a-z0-9_]{0,31}".toRegex())) {

                    removeBack()

                    sudo make L.COMMAND_NAME_INVALID.input(
                        L.CANCEL_NOTIFICATION
                    ) replyTo message

                    return

                }

                if (command == "start") {

                    sudo makeHtml L.COMMAND_START replyTo message

                    return

                }

                cache.command = command

                cache.step = 1

                removeBack()

                sudo make L.COMMAND_INPUT_DESCRIPTION sendTo chatId

            }

            1 -> {

                val text = message.text

                if (text == null || text.length !in 3..256) {

                    sudo make L.COMMAND_DESCRIPTION_INVALID.input(
                        L.CANCEL_NOTIFICATION + "\n" + L.PREVIOUS_NOTIFICATION
                    ) replyTo message

                    return

                }

                cache.description = text

                cache.step = 2

                if (cache.botId != me.id) {

                    sudo removePersist userId

                    val userBot = findUserBot(cache.botId)!!

                    launcher.initBot(userBot).apply {

                        writePersist(userId, persistId, subId, * data, allowFunction = true)

                        sudo make L.INPUT_MESSAGES sendTo chatId

                    }

                    sudo make L.JUMP_TO_SET.input(userBot.username) sendTo chatId

                } else {

                    sudo make L.INPUT_MESSAGES sendTo chatId

                }


            }

            2 -> {

                val content = message.asInputOrForward

                cache.messages.add(content)

                if (content is TdApi.InputMessageForwarded) {

                    sudo make L.MESSAGE_ADDED_FWD sendTo chatId

                } else {

                    sudo make L.MESSAGE_ADDED sendTo chatId

                }

            }

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

            val cache = data[0] as CreateBotCommandCache

            if (!cache.edited) {

                editMessageReplyMarkupOrNull(chatId, cache.startsAt, null)

                cache.edited = true

            }

            launcher.findHandler<CommandsMenu>()
                .commandsMenu(cache.botId, findUserBot(cache.botId), userId, chatId, 0L, false)

        }

    }

}