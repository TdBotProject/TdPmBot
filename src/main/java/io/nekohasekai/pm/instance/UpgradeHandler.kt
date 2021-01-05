package io.nekohasekai.pm.instance

import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.ktlib.td.extensions.userCalled
import io.nekohasekai.pm.database.PmInstance
import io.nekohasekai.pm.launcher
import td.TdApi

class UpgradeHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        val integration = integration

        val content = message.content

        if (chatId != integration?.integration || content !is TdApi.MessageChatUpgradeTo) return

        val targetSupergroup = -1000000000000L - content.supergroupId

        // assert((getChat(targetSupergroup).type as? TdApi.ChatTypeSupergroup)?.supergroupId == content.supergroupId)

        userCalled(userId, "upgrade to $targetSupergroup")

        database {

            integration.integration = targetSupergroup

        }

        launcher.botIntegrations.fetch(me.id).apply {

            value = integration
            changed = true

            flush()

        }

    }

}