package io.github.nekohasekai

import cn.hutool.core.util.NumberUtil
import io.github.nekohasekai.nekolib.cli.TdCli
import io.github.nekohasekai.nekolib.cli.TdLoader
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.utils.GetIdCommand
import io.github.nekohasekai.pm.DeleteHadler
import io.github.nekohasekai.pm.InputHandler
import io.github.nekohasekai.pm.OutputHandler
import io.github.nekohasekai.pm.database.MessageRecordDao
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.sqlite.jdbc3.JDBC3DatabaseMetaData

object Launcher : TdCli() {

    init {

        // https://github.com/JetBrains/Exposed/issues/1007

        JDBC3DatabaseMetaData::class.java.getDeclaredField("driverName").apply {

            isAccessible = true

            set(null, "SQLite")

        }

    }

    val public get() = booleanEnv("PUBLIC")
    val admins
        get() = (stringEnv("ADMINS") ?: "")
                .split(" ")
                .filter { NumberUtil.isInteger(it) }
                .map { it.toInt() }

    val database = Database.connect(openDatabase(options.databaseDirectory, "pm_data.db"))

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {

        readSettings("pm.conf")?.insertProperties()

        if (admins.isEmpty()) {

            defaultLog.warn("Admin not specified, use /id to get your userid.")

        }

        TdLoader.tryLoad()

        start()

        if (waitForAuth()) {
        }

    }

    override suspend fun onLogin() {

        addHandler(GetIdCommand())

        if (public) {

        } else if (admins.isNotEmpty()) {

            if (admins.size > 1) {

                defaultLog.warn("More than one id is specified in non-public mode, others will be ignored.")

            }

            addHandler(SingleInstance(me.id))

        }

    }

    class SingleInstance(botId: Int) : TdHandler(), PmInstance {

        override val database = Launcher.database
        override val messageRecords = MessageRecords(botId)
        override val messages = MessageRecordDao(messageRecords)

        override fun onLoad() {

            database {

                SchemaUtils.create(messageRecords)

            }

            addHandler(InputHandler(admins[0], this))
            addHandler(OutputHandler(admins[0], this))
            addHandler(DeleteHadler(admins[0], this))

        }

    }

}