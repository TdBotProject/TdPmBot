package io.github.nekohasekai.pm.instance

import cn.hutool.core.date.SystemClock
import io.github.nekohasekai.nekolib.core.client.TdHandler
import io.github.nekohasekai.nekolib.core.raw.getChat
import io.github.nekohasekai.nekolib.core.raw.getUser
import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.pm.database.MessageRecords
import io.github.nekohasekai.pm.database.PmInstance
import td.TdApi
import java.util.concurrent.atomic.AtomicBoolean

class InputHandler(private val admin: Int, pmInstance: PmInstance) : TdHandler(), PmInstance by pmInstance {

    private var currentUser = 0
    private var times = 0
    private var inited by AtomicBoolean()

    override suspend fun onNewMessage(userId: Int, chatId: Long, message: TdApi.Message) {

        if (userId == me.id || userId == admin || !message.fromPrivate) return

        if (!inited) {

            getChat(admin.toLong())

            inited = true

        }

        database {

            messages.new(message.id) {

                type = MessageRecords.MESSAGE_TYPE_INPUT_MESSAGE

                this.chatId = chatId

                createAt = (SystemClock.now() / 100L).toInt()

            }

        }

        if (userId != currentUser || times < 1) {

            val user = getUser(userId)

            val inputNotice = sudo makeHtml "From: ${user.asIdMention}\nName: ${user.displayNameHtml}" syncTo admin

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

        val forwardedMessage = sudo makeForward message syncTo admin

        database {

            messages.new(forwardedMessage.id) {

                type = MessageRecords.MESSAGE_TYPE_INPUT_FORWARDED

                this.chatId = chatId

                targetId = message.id

                createAt = (SystemClock.now() / 100L).toInt()

            }

        }

    }

}