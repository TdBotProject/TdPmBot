package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdException
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.pm.INPUT_NOTICE
import io.github.nekohasekai.pm.database.BotIntegration
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import td.TdApi
import java.util.concurrent.atomic.AtomicBoolean

class InputHandler(pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    private var currentUser = 0
    private var times = 0
    private var inited by AtomicBoolean()

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        val integration = integration

        if (admin == chatId || chatId == integration?.integration || !message.fromPrivate) return

        if (!inited) {

            getChat(admin)

            inited = true

        }

        database {

            messages.new(message.id) {

                type = MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE

                this.chatId = chatId

                createAt = (SystemClock.now() / 100L).toInt()

            }

        }

        suspend fun MessageFactory.syncToTarget(): TdApi.Message? {

            if (integration != null && !integration.paused) {

                try {

                    return this syncTo integration.integration

                } catch (e: TdException) {

                    // failed

                    integration.paused = true

                    BotIntegration.Cache.fetch(me.id).changed = true
                    BotIntegration.Cache.remove(me.id)

                    // TODO: SEND PAUSED MESSAGE

                }

            }

            try {

                return this syncTo admin

            } catch (e: TdException) {

                defaultLog.warn(e, "banned by owner")

                // TODO: PROCESS BOT BANNED BY OWNER

            }

            return null

        }

        if (userId != currentUser || times < 1) {

            val user = getUser(userId)

            val inputNotice = (sudo makeHtml L.INPUT_NOTICE.input(user.asIdMention, user.displayNameHtml)).syncToTarget()
                    ?: return

            database {

                messages.new(inputNotice.id) {

                    type = MessageRecords.MESSAGE_TYPE_INPUT_NOTICE

                    this.chatId = chatId

                    createAt = (SystemClock.now() / 100L).toInt()

                }

            }

            currentUser = userId
            times = 4

        } else {

            times--

        }

        val forwardedMessage = (sudo makeForward message).syncToTarget() ?: return

        database {

            messages.new(forwardedMessage.id) {

                type = MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED

                this.chatId = chatId

                targetId = message.id

                createAt = (SystemClock.now() / 100L).toInt()

            }

        }

        finishEvent()

    }

}