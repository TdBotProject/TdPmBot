package io.github.nekohasekai.pm.manage

import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.*
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.BotIntegration
import io.github.nekohasekai.pm.database.UserBot
import td.TdApi

class SetIntegration : UserBotSelector(true) {

    companion object {

        const val function = "set_integration"

        const val dataId = DATA_SET_START_INTEGRATION

        val DEF = TdApi.BotCommand(
                function,
                LocaleController.SET_INTEGRATION_DEF
        )

    }

    override val persistId = PERSIST_SET_START_INTEGRATION


    override fun onLoad() {

        if (sudo is Launcher) {

            super.onLoad()

            initFunction(function)

            initData(dataId)

        }

        initStartPayload(function)

    }

    override suspend fun onFunction(userId: Int, chatId: Long, message: TdApi.Message, function: String, param: String, params: Array<String>, originParams: Array<String>) {

        if (!Launcher.public && chatId != Launcher.admin) rejectFunction()

        if (!message.fromPrivate) {

            userCalled(userId, "set integration in non-private chat")

            sudo make LocaleController.FN_PRIVATE_ONLY onSuccess deleteDelay(message) replyTo message

            return

        }

        val L = LocaleController.forChat(userId)

        doSelect(L, userId, 0L, L.SELECT_TO_SET)

    }

    val integrationActionMessages = hashMapOf<Int, Long>()

    override suspend fun onSelected(userId: Int, chatId: Long, subId: Long, userBot: UserBot?) {

        val L = LocaleController.forChat(userId)

        if (userBot == null) {

            userCalled(userId, "set launcher`s integration")

        } else {

            userCalled(userId, "set @${userBot.username}`s integration")

        }

        startSet(L, userBot?.botId ?: me.id, userBot?.username ?: me.username, userId, chatId, 0L, true)

    }

    fun startSet(L: LocaleController, botUserId: Int, botUserName: String, userId: Int, chatId: Long, messageId: Long, send: Boolean) {

        if (integrationActionMessages.containsKey(userId)) {

            val messageIdToDelete = integrationActionMessages[userId]!!

            if (messageIdToDelete != messageId) delete(chatId, integrationActionMessages.remove(userId)!!)

        }

        val integration = BotIntegration.Cache.fetch(botUserId).value

        var content = L.SET_INTEGRATION.input(botUserName, when {

            integration == null -> L.INTEGRATION_UNDEF
            integration.paused -> L.INTEGRATION_PAUSED
            else -> L.INTEGRATION_OK

        })

        if (integration != null) {

            content += "\n\n" + L.INTEGRATION_STATUS.input(if (integration.adminOnly) L.ENABLED else L.DISABLED)

        }

        sudo make content withMarkup inlineButton {

            urlLine(L.INTEGRATION_SET, mkStartGroupPayloadUrl(botUserName, "set_integration"))

            if (integration != null) {

                if (integration.adminOnly) {

                    dataLine(L.INTEGRATION_DISABLE_ADMIN_ONLY, dataId, botUserId.toByteArray(), 0.toByteArray())

                } else {

                    dataLine(L.INTEGRATION_ENABLE_ADMIN_ONLY, dataId, botUserId.toByteArray(), 1.toByteArray())

                }

                /*   if (integration.cleanMode) {

                       dataLine(L.INTEGRATION_DISABLE_CLEAN_MODE, dataId, botUserId.toByteArray(), 4.toByteArray())

                   } else {

                       dataLine(L.INTEGRATION_ENABLE_CLEAN_MODE, dataId, botUserId.toByteArray(), 5.toByteArray())

                   }*/

                newLine {

                    if (!integration.paused) {

                        dataButton(L.INTEGRATION_PAUSE, dataId, botUserId.toByteArray(), 2.toByteArray())

                    } else {

                        dataButton(L.INTEGRATION_RESUME, dataId, botUserId.toByteArray(), 3.toByteArray())

                    }

                    // dataButton(L.INTEGRATION_DEL, dataId, botUserId.toByteArray(), 6.toByteArray())

                }

            }

        } onSuccess {

            integrationActionMessages[userId] = it.id

        } at messageId edit !send sendOrEditTo chatId

    }

    override suspend fun onNewCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>) {

        val L = LocaleController.forChat(userId)

        val action: Int

        val botUserId = try {

            action = data[1].toInt()

            data[0].toInt()

        } catch (e: Exception) {

            warnUserCalled(userId, "invalid set integration callback")

            delete(chatId, messageId)

            sudo makeAnswer { cacheTime = 114 } answerTo queryId

            // 非法请求

            return

        }

        val userBot = database { UserBot.findById(botUserId) }

        if (userId.toLong() != Launcher.admin && userBot?.owner != userId) {

            // 权限检查

            warnUserCalled(userId, """
                Illegal access to set_integration callback

                User: ${getUser(userId).displayName}
                UserId: $userId
                TargetBotId: $botUserId
                Action: $action
            """.trimIndent())

            sudo makeAnswer { cacheTime = 114 } answerTo queryId

            return

        }

        val integration = BotIntegration.Cache.fetch(botUserId).value

        if (integration == null) {

            // 过期的消息

            delete(chatId, messageId)

            startSet(L, me.id, me.username, userId, chatId, 0L, true)

            return

        }

        if (action == 0) {

            // disable admin only

            database.write {

                integration.adminOnly = false
                integration.flush()

            }

            sudo makeAnswer L.DISABLED answerTo queryId

        } else if (action == 1) {

            database.write {

                integration.adminOnly = true
                integration.flush()

            }

            sudo makeAnswer L.ENABLED answerTo queryId

        } else if (action == 2) {

            database.write {

                integration.paused = true
                integration.flush()

            }

            sudo makeAnswer L.DISABLED answerTo queryId

        } else if (action == 3) {

            database.write {

                integration.paused = false
                integration.flush()

            }

            sudo makeAnswer L.ENABLED answerTo queryId

            /*} else if (action == 4) {

                database.write {

                    integration.cleanMode = false
                    integration.flush()

                }

                sudo makeAnswer L.DISABLED answerTo queryId

            } else if (action == 5) {

                database.write {

                    integration.cleanMode = true
                    integration.flush()

                }

                sudo makeAnswer L.ENABLED answerTo queryId*/

        }

        BotIntegration.Cache.fetch(botUserId).value = integration

        startSet(L, botUserId, userBot?.username ?: me.username, userId, chatId, messageId, false)

    }

    override suspend fun onStartPayload(userId: Int, chatId: Long, message: TdApi.Message, payload: String, params: Array<String>) {

        val L = LocaleController.forChat(userId)

        if (userId.toLong() != Launcher.admin && database { UserBot.findById(me.id)?.owner != userId }) {

            // 权限检查

            warnUserCalled(userId, """
                Illegal access to set integration payload
                
                Chat: ${getChat(chatId).title}
                ChatId: $chatId
                User: ${getUser(userId).displayName}
                UserId: $userId
            """.trimIndent())

            sudo make L.NO_PERMISSION syncReplyTo message

            return

        }

        val integrationEntry = BotIntegration.Cache.fetch(me.id)

        val integration = integrationEntry.value

        var changed = false

        if (integration == null) {

            BotIntegration.Cache.remove(me.id)

            database.write {

                BotIntegration.new(me.id) {

                    this.integration = chatId
                    this.adminOnly = false
                    this.paused = false

                }

            }

            changed = true

        } else if (integration.integration != chatId) {

            database.write {

                integration.integration = chatId
                integration.flush()

            }

            changed = true

        } else if (integration.paused) {

            database.write {

                integration.paused = false
                integration.flush()

            }

            changed = true

        }

        sudo make L.INTEGRATION_HAS_SET syncReplyTo message

        if (changed) {

            if (integrationActionMessages.containsKey(userId)) {

                startSet(L, me.id, me.username, userId, userId.toLong(), integrationActionMessages[userId]!!, false)

            }

        }

    }

}