package io.nekohasekai.pm.manage.menu

import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.td.cli.database
import io.nekohasekai.ktlib.td.core.raw.getChat
import io.nekohasekai.ktlib.td.core.raw.getUser
import io.nekohasekai.ktlib.td.extensions.*
import io.nekohasekai.ktlib.td.i18n.*
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.BotIntegration
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.instance.PmBot
import io.nekohasekai.pm.manage.BotHandler
import io.nekohasekai.pm.manage.MyBots
import td.TdApi

class IntegrationMenu : BotHandler() {

    companion object {

        const val payload = "set_integration"

        const val dataId = DATA_SET_START_INTEGRATION

    }

    override fun onLoad() {

        if (sudo is TdPmBot) {

            initData(dataId)

        }

        initStartPayload(payload)

    }

    suspend fun integrationMenu(L: LocaleController, botUserId: Int, userBot: UserBot?, userId: Int, chatId: Long, messageId: Long, isEdit: Boolean) {

        val integration = launcher.botIntegrations.fetch(botUserId).value

        val botUserName = botUserName(botUserId, userBot)

        var content = L.SET_INTEGRATION.input(
                botName(botUserId, userBot),
                botUserName(botUserId, userBot), when {
            integration == null -> L.INTEGRATION_UNDEF
            integration.paused -> L.INTEGRATION_PAUSED
            else -> L.INTEGRATION_OK
        })

        if (integration != null) {

            content += "\n\n" + L.INTEGRATION_STATUS.input(if (integration.adminOnly) L.ENABLED else L.DISABLED)

        }

        sudo make content withMarkup inlineButton {

            urlLine(L.INTEGRATION_SET, mkStartGroupPayloadUrl(botUserName, payload))

            val botId = botUserId.asByteArray()

            if (integration != null) {

                if (integration.adminOnly) {

                    dataLine(L.INTEGRATION_DISABLE_ADMIN_ONLY, dataId, botId, byteArrayOf(0))

                } else {

                    dataLine(L.INTEGRATION_ENABLE_ADMIN_ONLY, dataId, botId, byteArrayOf(1))

                }

                newLine {

                    if (!integration.paused) {

                        dataButton(L.INTEGRATION_PAUSE, dataId, botId, byteArrayOf(2))

                    } else {

                        dataButton(L.INTEGRATION_RESUME, dataId, botId, byteArrayOf(3))

                    }

                    dataButton(L.INTEGRATION_DEL, dataId, botId, byteArrayOf(4))

                }

            }

            dataLine(L.BACK_ARROW, BotMenu.dataId, botId)

        } onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        val L = localeFor(userId)

        if (data.isEmpty()) {

            integrationMenu(L, userBot?.botId ?: me.id, userBot, userId, chatId, messageId, true)

            return

        }

        val action = data[0][0].toInt()

        val integration = launcher.botIntegrations.fetch(botUserId).value

        if (integration == null) {

            // 过期的消息

            delete(chatId, messageId)

            findHandler<MyBots>().rootMenu(userId, chatId, 0L, false)

            return

        }

        when (action) {

            0 -> {

                // disable admin only

                database.write {

                    integration.adminOnly = false
                    integration.flush()

                }

                sudo makeAnswer L.DISABLED answerTo queryId

            }

            1 -> {

                database.write {

                    integration.adminOnly = true
                    integration.flush()

                }

                sudo makeAnswer L.ENABLED answerTo queryId

            }

            2 -> {

                database.write {

                    integration.paused = true
                    integration.flush()

                }

                sudo makeAnswer L.DISABLED answerTo queryId

            }

            3 -> {

                database.write {

                    integration.paused = false
                    integration.flush()

                }

                sudo makeAnswer L.ENABLED answerTo queryId

            }

            4 -> {

                database.write {

                    integration.delete()

                }

                sudo makeAnswer L.DISABLED answerTo queryId

            }

        }

        launcher.botIntegrations.fetch(botUserId).value = if (action < 4) integration else null

        integrationMenu(L, botUserId, userBot, userId, chatId, messageId, true)

    }

    override suspend fun onStartPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        val L = localeFor(userId)

        if (message.senderChatId == 0L && userId.toLong() != launcher.admin && database { UserBot.findById(me.id)?.owner != userId }) {

            warnUserCalled(userId, """
                Illegal access to set integration payload
                
                Chat: ${getChat(chatId).title}
                ChatId: $chatId
                User: ${getUser(userId).displayName}
                UserId: $userId
            """.trimIndent())

            sudo make L.NO_PERMISSION onSuccess deleteDelay(message) replyTo message

            return

        }

        if (message.senderChatId == 0L) {

            confirmSet(chatId)

            sudo make L.INTEGRATION_HAS_SET syncReplyTo message

        } else {

            sudo make L.INTEGRATION_CONFIRM withMarkup inlineButton {

                dataLine(L.INTEGRATION_SET, dataId)

            } replyTo message

        }

    }

    override suspend fun onNewCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>) {

        if (data.isNotEmpty()) {

            super.onNewCallbackQuery(userId, chatId, messageId, queryId, data)

            return

        }

        val L = localeFor(userId)

        if (userId.toLong() != launcher.admin && database { UserBot.findById(me.id)?.owner != userId }) {

            sudo makeAlert L.NO_PERMISSION cacheTime 114 syncAnswerTo queryId

            return

        }

        confirmSet(chatId)

        sudo syncConfirmTo queryId

        sudo make L.INTEGRATION_HAS_SET at messageId syncEditTo chatId

    }

    suspend fun confirmSet(chatId: Long) {

        val integrationEntry = launcher.botIntegrations.fetch(me.id)

        val integration = integrationEntry.value

        when {

            integration == null -> {

                launcher.botIntegrations.remove(me.id)

                database.write {

                    BotIntegration.new(me.id) {

                        this.integration = chatId

                    }

                }

            }

            integration.integration != chatId -> {

                database.write {

                    integration.integration = chatId
                    integration.flush()

                }

            }

            integration.paused -> {

                database.write {

                    integration.paused = false
                    integration.flush()

                }

            }

        }

        val userId = ((sudo as? PmBot)?.admin ?: launcher.admin).toInt()
        val botUserId = me.id
        val userBot = userBot

        launcher.apply {

            val actionMessage = actionMessages.fetch(userId)

            if (actionMessage.value != null) {

                findHandler<IntegrationMenu>().integrationMenu(localeFor(userId), botUserId, userBot, userId, userId.toLong(), actionMessage.value!!.messageId, true)

            }

        }

    }

}