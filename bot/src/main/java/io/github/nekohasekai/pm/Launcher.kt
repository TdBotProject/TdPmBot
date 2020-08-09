package io.github.nekohasekai.pm

import cn.hutool.core.util.NumberUtil
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
import io.github.nekohasekai.pm.manage.SetStartMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.objenesis.instantiator.util.UnsafeUtils
import td.TdApi

object Launcher : TdCli() {

    private val public get() = booleanEnv("PUBLIC")

    val admins by lazy {
        (stringEnv("ADMINS") ?: "")
                .split(" ")
                .filter { NumberUtil.isInteger(it) }
                .map { it.toInt() }
                .toIntArray()
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {

        val unsafe = UnsafeUtils.getUnsafe()

        val loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger")
        val loggerField = loggerClass.getDeclaredField("logger")

        unsafe.putObjectVolatile(loggerClass, unsafe.staticFieldOffset(loggerField), null)

        readSettings("pm.conf")?.insertProperties()

        val logLevel = stringEnv("LOG_LEVEL").takeIf { !it.isNullOrBlank() }?.toUpperCase() ?: "INFO"

        runCatching {

            LOG_LEVEL = Level.valueOf(logLevel)

        }.onFailure {

            LOG_LEVEL = Level.INFO

            defaultLog.error("Invalid log level $logLevel, fallback to INFO.")

        }

        TdLoader.tryLoad()

        if (admins.isEmpty()) {

            defaultLog.warn("Admin not specified, use /id to get your userid.")

        }

        start()

    }

    override fun onLoad() {

        super.onLoad()

        defaultLog.debug("Init databases")

        initDatabase("pm_data.db")

        LocaleController.initWithDatabase(database)

        if (public) {

            initPersistDatabase()

            database {

                SchemaUtils.createMissingTablesAndColumns(
                        UserBots,
                        StartMessages
                )

            }

            initFunction("help")

            addHandler(LocaleSwitcher(DATA_SWITCH_LOCALE) { userId, chatId, message ->

                onLaunch(userId, chatId, message)

            })

            addHandler(CreateBot())
            addHandler(DeleteBot())
            addHandler(SetStartMessages())

        } else if (admins.size > 1) {

            defaultLog.warn("More than one id is specified in non-public mode, others will be ignored.")

        }

        addHandler(GetIdCommand())

    }

    override suspend fun onLogin() {

        if (public) {

            upsertCommands(
                    findHandler<CreateBot>().DEF,
                    findHandler<DeleteBot>().DEF,
                    findHandler<SetStartMessages>().DEF,
                    findHandler<LocaleSwitcher>().DEF,
                    HELP_COMMAND,
                    CANCEL_COMMAND
            )

            database {

                BotInstances.loadAll()

            }

        }

        if (admins.isNotEmpty()) {

            addHandler(SingleInstance(me.id))

            if (!public) upsertCommands()

        }

    }

    const val repoName = "TdPmBot"
    const val repoUrl = "https://github.com/TdBotProject/TdPmBot"
    const val licenseUrl = "https://github.com/TdBotProject/TdPmBot/blob/master/LICENSE"

    override suspend fun onLaunch(userId: Int, chatId: Long, message: TdApi.Message) {

        if (!message.fromPrivateOrdelete) return

        val L = LocaleController.forChat(userId)

        val startMessages = StartMessages.Cache.fetch(me.id).value

        if (!public && !admins.contains(userId)) {

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

        if (!admins.contains(userId)) {

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

    class SingleInstance(botId: Int) : TdHandler(), PmInstance {

        override val messageRecords = MessageRecords(botId)
        override val messages = MessageRecordDao(messageRecords)

        override val L get() = LocaleController.forChat(admins[0])

        override fun onLoad() {

            database {

                SchemaUtils.create(messageRecords)

            }

            addHandler(InputHandler(admins, this))
            addHandler(OutputHandler(admins[0], this))
            addHandler(DeleteHandler(admins[0], this))
            addHandler(JoinHandler(admins[0], this))

        }

    }

}