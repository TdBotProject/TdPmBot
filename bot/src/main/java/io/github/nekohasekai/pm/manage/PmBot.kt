package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.Launcher
import io.github.nekohasekai.nekolib.core.client.TdClient
import io.github.nekohasekai.nekolib.core.raw.checkAuthenticationBotToken
import io.github.nekohasekai.nekolib.core.utils.defaultLog
import io.github.nekohasekai.nekolib.core.utils.displayName
import io.github.nekohasekai.nekolib.core.utils.invoke
import io.github.nekohasekai.pm.database.MessageRecordDao
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import io.github.nekohasekai.pm.instance.DeleteHadler
import io.github.nekohasekai.pm.instance.InputHandler
import io.github.nekohasekai.pm.instance.JoinHandler
import io.github.nekohasekai.pm.instance.OutputHandler
import org.jetbrains.exposed.sql.SchemaUtils
import td.TdApi

class PmBot(val botToken: String, val owner: Int) : TdClient(), PmInstance {

    val botUserId = botToken.substringBefore(":").toInt()

    override val database = Launcher.database
    override val messageRecords = MessageRecords(botUserId)
    override val messages = MessageRecordDao(messageRecords)

    override suspend fun onAuthorizationState(authorizationState: TdApi.AuthorizationState) {

        if (authorizationState is TdApi.AuthorizationStateWaitPhoneNumber) {

            checkAuthenticationBotToken(botToken)

        }

        super.onAuthorizationState(authorizationState)

    }

    override suspend fun onLogin() {

        defaultLog.debug("Pm Bot Loaded: ${me.displayName}")

    }

    override fun onLoad() {

        options databaseDirectory "data/pm/$botUserId"

        database {

            SchemaUtils.create(messageRecords)

        }

        addHandler(InputHandler(owner, this))
        addHandler(OutputHandler(owner, this))
        addHandler(DeleteHadler(owner, this))
        addHandler(JoinHandler(owner, this))

    }

}