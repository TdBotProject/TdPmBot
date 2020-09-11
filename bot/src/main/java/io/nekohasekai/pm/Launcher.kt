package io.nekohasekai.pm

import cn.hutool.core.date.DateUtil
import cn.hutool.core.date.SystemClock
import cn.hutool.core.io.FileUtil
import io.nekohasekai.ktlib.compress.*
import io.nekohasekai.ktlib.core.*
import io.nekohasekai.ktlib.td.cli.TdCli
import io.nekohasekai.ktlib.td.core.extensions.*
import io.nekohasekai.ktlib.td.core.i18n.*
import io.nekohasekai.ktlib.td.core.i18n.store.*
import io.nekohasekai.ktlib.td.core.persists.store.DatabasePersistStore
import io.nekohasekai.ktlib.td.core.raw.getChatWith
import io.nekohasekai.ktlib.td.core.utils.*
import io.nekohasekai.ktlib.td.core.utils.commands.GetIdCommand
import io.nekohasekai.pm.database.*
import io.nekohasekai.pm.instance.*
import io.nekohasekai.pm.manage.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import td.TdApi
import java.io.File
import java.util.*
import kotlin.concurrent.schedule
import kotlin.system.exitProcess

object Launcher : TdCli(), PmInstance {

    var public = false
    lateinit var whiteList: IntArray

    fun userAccessible(userId: Int): Boolean {

        if (userId == admin.toInt()) return true

        if (!public) return false

        if (!::whiteList.isInitialized) return true

        return whiteList.contains(userId)

    }

    @Suppress("ObjectPropertyName")
    private var _admin = 0L

    override val admin by ::_admin

    lateinit var users: Array<Long>

    override val integration get() = BotIntegration.Cache.fetch(me.id).value
    override val settings get() = BotSetting.Cache.fetch(me.id).value
    override val blocks by lazy { UserBlocks.Cache(me.id) }

    override var configFile = File("pm.yml")

    @JvmStatic
    fun main(args: Array<String>) {

        launch(args)

        loadConfig()

        start()

        if (admin == 0L) {

            defaultLog.warn("Bot owner not specified, send /id to bot to get your userId.")

        }

    }

    override fun onLoadConfig() {

        super.onLoadConfig()

        _admin = intConfig("B0T_OWNER")?.toLong() ?: _admin

        when (val pmMode = stringConfig("PM_MODE")?.toLowerCase() ?: "public") {

            "public" -> {

                public = true

            }

            "white-list" -> {

                public = true

                val accessibleUsers = listConfig("PM_WHITE_LIST")

                whiteList = accessibleUsers?.mapNotNull { item ->

                    runCatching { item.toInt() }.onFailure {

                        defaultLog.warn(">> Invalid white-list user-id item: $item", it)

                    }.getOrNull()

                }?.toIntArray() ?: intArrayOf()

            }

            "private" -> {

                public = false

            }

            else -> {

                defaultLog.error(">> Invalid mode defined: $pmMode")

                exitProcess(100)

            }

        }

    }

    override fun onArgument(argument: String, value: String?) {

        if (argument == "backup") {

            var backupTo: File

            backupTo = File(value ?: ".")

            if (backupTo.isDirectory) {

                @Suppress("SpellCheckingInspection")
                backupTo = File(backupTo, "td-pm-backup-${DateUtil.format(Date(), "yyyyMMdd-HHmmss")}.tar.xz")

            } else if (!backupTo.name.endsWith(".tar.xz")) {

                defaultLog.error(">> File name must ends with .tar.xz")

                exitProcess(100)

            }

            backupTo = backupTo.canonicalFile

            val output = FileUtil.touch(backupTo).tarXz()

            output.writeFile("pm.conf")

            // 数据目录

            output.writeDirectory("data/")

            // 数据库

            output.writeFile("data/pm_data.db")

            output.writeFile("data/td.binlog")

            val pmBots = File("data/pm").listFiles()

            if (!pmBots.isNullOrEmpty()) {

                output.writeDirectory("data/pm/")

                pmBots.forEach {

                    output.writeDirectory("data/pm/${it.name}/")

                    output.writeFile("data/pm/${it.name}/td.binlog")

                }

            }

            output.finish()

            output.close()

            defaultLog.info(">> Saved to ${backupTo.path}")

            exitProcess(0)

        } else super.onArgument(argument, value)

    }

    override fun onLoad() {

        super.onLoad()

        defaultLog.debug("Init databases")

        initDatabase("pm_data.db")

        if (LocaleStore.store is InMemoryLocaleStore) {

            LocaleStore.setImplement(DatabaseLocaleStore(database))

        }

        persists.setImplement(DatabasePersistStore(database))

        database.write {

            SchemaUtils.createMissingTablesAndColumns(
                    UserBots,
                    StartMessages,
                    ActionMessages,
                    BotIntegrations,
                    BotSettings,
                    BotCommands,
                    MessageRecords,
                    UserBlocks
            )

        }

        if (public) initFunction("help")

        addHandler(LocaleSwitcher(DATA_SWITCH_LOCALE) { userId, chatId, message ->

            onLaunch(userId, chatId, message)

        })

        addHandler(CreateBot())
        addHandler(MyBots())

        addHandler(InputHandler(this))
        addHandler(OutputHandler(this))
        addHandler(EditHandler(this))
        addHandler(DeleteHandler(this))
        addHandler(JoinHandler(this))
        addHandler(RecallHandler(this))

        addHandler(GetIdCommand())
        addHandler(AdminCommands())

    }

    suspend fun updateCommands() {

        val commands = database {
            BotCommands
                    .select { commandsForCurrentBot and (BotCommands.hide eq false) }
                    .map { TdApi.BotCommand(it[BotCommands.command], it[BotCommands.description]) }
                    .toTypedArray()
        }

        if (public) {

            upsertCommands(
                    findHandler<CreateBot>().def(),
                    findHandler<MyBots>().def(),
                    findHandler<LocaleSwitcher>().def(),
                    * commands,
                    HELP_COMMAND,
                    CANCEL_COMMAND
            )

        } else {

            upsertCommands(* commands)

        }

    }

    override suspend fun skipFloodCheck(senderUserId: Int, message: TdApi.Message) = senderUserId == admin.toInt()

    override suspend fun onLogin() {

        updateCommands()

        database {

            BotInstances.loadAll()

        }

        timer.schedule(Date(nextDay()), 24 * 60 * 60 * 1000L) {

            GlobalScope.launch(Dispatchers.IO) { gc() }

        }

    }

    override suspend fun gc() {

        defaultLog.debug(">> 执行垃圾回收")

        defaultLog.debug(">> 内存缓存")

        super.gc()

        BotIntegration.Cache.clear()
        BotSetting.Cache.clear()
        ActionMessage.Cache.clear()
        StartMessages.Cache.clear()

        defaultLog.debug(">> 清理数据库")

        val time = (SystemClock.now() / 1000L).toInt() - 24 * 60 * 60

        database.write {

            val result = ActionMessage.find { ActionMessages.createAt less time }

            result.forEach { row ->

                val chatId = row.userId.toLong()

                getChatWith(chatId) {

                    onSuccess {

                        delete(chatId, row.messageId)

                    }

                }

            }

            ActionMessages.deleteWhere { ActionMessages.createAt less time }

            val existsBots = UserBot.all().map { it.botId }

            BotCommands.deleteWhere { BotCommands.botId notInList existsBots }
            BotIntegrations.deleteWhere { BotIntegrations.botId notInList existsBots }
            BotSettings.deleteWhere { BotSettings.botId notInList existsBots }
            MessageRecords.deleteWhere { MessageRecords.botId notInList existsBots }
            StartMessages.deleteWhere { StartMessages.botId notInList existsBots }
            UserBlocks.deleteWhere { UserBlocks.botId notInList existsBots }

        }

        defaultLog.trace(">> ${me.displayNameFormatted}")

        gc(this)

        BotInstances.instanceMap.forEach {

            defaultLog.trace(">> ${it.value.me.displayNameFormatted}")

            it.value.gc()

        }

        defaultLog.debug("<< 执行垃圾回收")

    }

    const val repoName = "TdPmBot"
    const val repoUrl = "https://github.com/TdBotProject/TdPmBot"
    const val licenseUrl = "https://github.com/TdBotProject/TdPmBot/blob/master/LICENSE"

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        if (blocks.fetch(userId).value == true) finishEvent()

        super.onNewMessage(userId, chatId, message)

        if (public) {

            onLaunch(userId, chatId, message)

            finishEvent()

        }

    }

    override suspend fun onUndefinedFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (function == "cancel") {

            if (!message.fromPrivateOrDelete) return

            if (!public) rejectFunction()

            super.onUndefinedFunction(userId, chatId, message, function, param, params, originParams)

            return

        }

        val command = BotCommands.Cache.fetch(me.id to function).value?.takeIf { !it.hide }

        if (!message.fromPrivate) {

            if (command == null) return

            command.messages.forEach { sudo make it syncTo chatId }

            return

        } else {

            if (command == null) rejectFunction()

            command.messages.forEach { sudo make it syncTo chatId }

            if (chatId != admin && !public) writePersist(userId, PERSIST_UNDER_FUNCTION, 0L, function)

        }

    }

    override suspend fun onUndefinedPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        if (message.fromPrivate) {

            val command = BotCommands.Cache.fetch(me.id to payload).value?.takeIf { !it.hide } ?: rejectFunction()

            command.messages.forEach { sudo make it syncTo chatId }

            if (!public && chatId != admin) writePersist(userId, PERSIST_UNDER_FUNCTION, 0L, payload)

        } else rejectFunction()

    }

    override suspend fun onLaunch(userId: Int, chatId: Long, message: TdApi.Message) {

        if (!message.fromPrivate) return

        val L = localeFor(userId)

        val startMessages = StartMessages.Cache.fetch(me.id).value

        if (!public && chatId != admin) {

            if (startMessages == null) {

                sudo make L.DEFAULT_WELCOME sendTo chatId

            } else {

                startMessages.forEach {

                    sudo make it syncTo chatId

                }

            }

            return

        }

        if (LocaleStore.localeRead(chatId) == null) {

            findHandler<LocaleSwitcher>().startSelect(L, chatId, true)

            return

        }

        sudo makeHtml L.LICENSE.input(repoName, licenseUrl, "Github Repo".htmlLink(repoUrl)) syncTo chatId

        delay(600L)

        if (startMessages == null) {

            sudo make L.HELP_MSG sendTo chatId

        } else {

            startMessages.forEach {

                sudo make it syncTo chatId

            }

        }

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        sudo make localeFor(chatId, userId).HELP_MSG sendTo chatId

    }

}