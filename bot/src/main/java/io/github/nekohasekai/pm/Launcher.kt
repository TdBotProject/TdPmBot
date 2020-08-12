package io.github.nekohasekai.pm

import cn.hutool.log.level.Level
import io.github.nekohasekai.nekolib.cli.TdCli
import io.github.nekohasekai.nekolib.cli.TdLoader
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.*
import io.github.nekohasekai.nekolib.utils.GetIdCommand
import io.github.nekohasekai.pm.database.*
import io.github.nekohasekai.pm.instance.*
import io.github.nekohasekai.pm.manage.CreateBot
import io.github.nekohasekai.pm.manage.DeleteBot
import io.github.nekohasekai.pm.manage.SetIntegration
import io.github.nekohasekai.pm.manage.SetStartMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import td.TdApi

object Launcher : TdCli() {

    val public get() = booleanEnv("PUBLIC")

    val admin by lazy { intEnv("ADMIN").toLong() }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {

        readSettings("pm.conf")?.insertProperties()

        val logLevel = stringEnv("LOG_LEVEL").takeIf { !it.isNullOrBlank() }?.toUpperCase() ?: "INFO"

        runCatching {

            LOG_LEVEL = Level.valueOf(logLevel)

        }.onFailure {

            LOG_LEVEL = Level.INFO

            defaultLog.error("Invalid log level $logLevel, fallback to INFO.")

        }

        TdLoader.tryLoad()

        if (admin == 0L) {

            defaultLog.warn("Admin not specified, use /id to get your userid.")

        }

        start()

    }

    override fun onLoad() {

        super.onLoad()

        defaultLog.debug("Init databases")

        initDatabase("pm_data.db")

        LocaleController.initWithDatabase(database)

        initPersistDatabase()

        database.write {

            SchemaUtils.createMissingTablesAndColumns(
                    UserBots,
                    StartMessages,
                    BotIntegrations
            )

        }

        initFunction("help")

        addHandler(LocaleSwitcher(DATA_SWITCH_LOCALE) { userId, chatId, message ->

            onLaunch(userId, chatId, message)

        })

        addHandler(CreateBot())
        addHandler(DeleteBot())
        addHandler(SetStartMessages())
        addHandler(SetIntegration())

        addHandler(GetIdCommand())

    }

    override suspend fun onLogin() {

        if (public) {

            upsertCommands(
                    findHandler<CreateBot>().DEF,
                    findHandler<DeleteBot>().DEF,
                    findHandler<SetStartMessages>().DEF,
                    findHandler<SetIntegration>().DEF,
                    findHandler<LocaleSwitcher>().DEF,
                    HELP_COMMAND,
                    CANCEL_COMMAND
            )

            database {

                BotInstances.loadAll()

            }

        }

        if (admin != 0L) {

            addHandler(SingleInstance(me.id))

            if (!public) upsertCommands()

        }

    }

    const val repoName = "TdPmBot"
    const val repoUrl = "https://github.com/TdBotProject/TdPmBot"
    const val licenseUrl = "https://github.com/TdBotProject/TdPmBot/blob/master/LICENSE"

    override suspend fun onUndefinedFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (!public && chatId != admin && message.fromPrivate) rejectFunction() else super.onUndefinedFunction(userId, chatId, message, function, param, params, originParams)

    }

    override suspend fun onUndefinedPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        if (!public && chatId != admin && message.fromPrivate) rejectFunction() else super.onUndefinedPayload(userId, chatId, message, payload, params)

    }

    override suspend fun onLaunch(userId: Int, chatId: Long, message: TdApi.Message) {

        if (!message.fromPrivate) return

        val L = LocaleController.forChat(userId)

        val startMessages = StartMessages.Cache.fetch(me.id).value

        if (!public && chatId != admin) {

            if (startMessages == null) {

                sudo make L.PRIVATE_INSTANCE sendTo chatId

            } else {

                startMessages.forEach {

                    sudo make it syncTo chatId

                }

            }

            return

        }

        if (LocaleController.chatLangMap.fetch(chatId).value == null) {

            findHandler<LocaleSwitcher>().startSelect(L, chatId, true)

            return

        }

        if (chatId != admin) {

            sudo makeHtml L.PUBLIC_WARN.input(repoUrl) syncTo chatId

            delay(600L)

        }

        sudo makeHtml L.LICENSE.input(repoName, licenseUrl, "Github Repo".toLink(repoUrl)) syncTo chatId

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

        val L = LocaleController.forChat(userId)

        sudo make L.HELP_MSG sendTo chatId

    }

    class SingleInstance(val botId: Int) : TdHandler(), PmInstance {

        override val messageRecords = MessageRecords(botId)
        override val messages = MessageRecordDao(messageRecords)

        override val L get() = LocaleController.forChat(admin)

        override val admin = Launcher.admin

        override val integration get() = BotIntegration.Cache.fetch(botId).value

        override fun onLoad() {

            database {

                SchemaUtils.createMissingTablesAndColumns(messageRecords)

            }

            addHandler(InputHandler(this))
            addHandler(OutputHandler(this))
            addHandler(EditHandler(this))
            addHandler(DeleteHandler(this))
            addHandler(JoinHandler(this))

            removeHandler(this)

        }

    }

}