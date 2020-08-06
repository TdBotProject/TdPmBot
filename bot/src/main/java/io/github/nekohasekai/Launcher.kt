package io.github.nekohasekai

import cn.hutool.core.util.NumberUtil
import cn.hutool.log.level.Level
import io.github.nekohasekai.nekolib.cli.TdCli
import io.github.nekohasekai.nekolib.cli.TdLoader
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.utils.GetIdCommand
import io.github.nekohasekai.pm.database.MessageRecordDao
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import io.github.nekohasekai.pm.database.UserBots
import io.github.nekohasekai.pm.instance.DeleteHadler
import io.github.nekohasekai.pm.instance.InputHandler
import io.github.nekohasekai.pm.instance.JoinHandler
import io.github.nekohasekai.pm.instance.OutputHandler
import io.github.nekohasekai.pm.manage.BotCreate
import io.github.nekohasekai.pm.manage.BotInstances
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils

object Launcher : TdCli() {

    private val public get() = booleanEnv("PUBLIC")

    val admins by lazy {
        (stringEnv("ADMINS") ?: "")
                .split(" ")
                .filter { NumberUtil.isInteger(it) }
                .map { it.toInt() }
    }

    init {

        initDatabase("pm_data.db")

    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {

        LOG_LEVEL = Level.ALL

        readSettings("pm.conf")?.insertProperties()

        if (admins.isEmpty()) {

            defaultLog.warn("Admin not specified, use /id to get your userid.")

        }

        TdLoader.tryLoad()

        start()

    }

    override suspend fun onLogin() {

        addHandler(GetIdCommand())

        if (public) {

            initPersistWithDefaultDatabase()

            database {

                SchemaUtils.create(UserBots)

                BotInstances.loadAll()

            }

            addHandler(BotCreate())

        } else if (admins.isNotEmpty()) {

            if (admins.size > 1) {

                defaultLog.warn("More than one id is specified in non-public mode, others will be ignored.")

            }

            addHandler(SingleInstance(me.id))

        }

    }

    class SingleInstance(botId: Int) : TdHandler(), PmInstance {

        override val messageRecords = MessageRecords(botId)
        override val messages = MessageRecordDao(messageRecords)

        override fun onLoad() {

            database {

                SchemaUtils.create(messageRecords)

            }

            addHandler(InputHandler(admins[0], this))
            addHandler(OutputHandler(admins[0], this))
            addHandler(DeleteHadler(admins[0], this))
            addHandler(JoinHandler(admins[0], this))

        }

    }

}