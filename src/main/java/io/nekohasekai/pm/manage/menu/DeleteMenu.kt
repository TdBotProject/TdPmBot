package io.nekohasekai.pm.manage.menu

import io.nekohasekai.ktlib.core.SQUARE_ENABLE
import io.nekohasekai.ktlib.core.input
import io.nekohasekai.ktlib.core.toStatusString
import io.nekohasekai.ktlib.td.core.raw.deleteFile
import io.nekohasekai.ktlib.td.core.raw.getMessage
import io.nekohasekai.ktlib.td.extensions.asByteArray
import io.nekohasekai.ktlib.td.i18n.BACK_ARROW
import io.nekohasekai.ktlib.td.i18n.localeFor
import io.nekohasekai.ktlib.td.utils.*
import io.nekohasekai.pm.*
import io.nekohasekai.pm.database.UserBot
import io.nekohasekai.pm.instance.backupToFile
import io.nekohasekai.pm.manage.BotHandler
import io.nekohasekai.pm.manage.MyBots
import td.TdApi
import java.util.*

class DeleteMenu : BotHandler() {

    companion object {

        const val dataId = DATA_DELETE_BOT_MENU

    }

    override fun onLoad() {

        initData(dataId)

    }

    fun deleteButtons(userId: Int, botUserId: Int, again: Boolean, export: Boolean) = inlineButton {

        val L = localeFor(userId)

        val botId = botUserId.asByteArray()

        if (!again) {

            dataLine(L.MENU_BOT_DEL_NO_1, BotMenu.dataId, botId)
            dataLine(L.MENU_BOT_DEL_NO_2, BotMenu.dataId, botId)
            dataLine(L.MENU_BOT_DEL_YES_1, dataId, botId, byteArrayOf(1))

        } else {

            dataLine(L.MENU_BOT_DEL_NO_3, BotMenu.dataId, botId)
            dataLine(L.MENU_BOT_DEL_NO_4, BotMenu.dataId, botId)
            dataLine(L.MENU_BOT_DEL_YES_2, dataId, botId, byteArrayOf(2))

        }

        Collections.shuffle(this)

        if (again) {
            newLine(true) {
                dataButton(L.MENU_BOT_DEL_EXPORT, -1)
                dataButton(export.toStatusString(), dataId, botId, byteArrayOf(0))
            }
        }

        dataLine(L.BACK_ARROW, BotMenu.dataId, botId)

    }

    suspend fun botDeleteMenu(
        botUserId: Int,
        userBot: UserBot?,
        userId: Int,
        chatId: Long,
        messageId: Long,
        isEdit: Boolean,
        again: Boolean
    ) {

        val L = localeFor(userId)

        sudo makeHtml (if (!again) L.MENU_BOT_DELETE_CONFIRM else L.MENU_BOT_DELETE_CONFIRM_AGAIN).input(

            botNameHtml(botUserId, userBot),
            botUserName(botUserId, userBot)

        ) withMarkup deleteButtons(userId, botUserId, again, false) onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit syncOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(
        userId: Int,
        chatId: Long,
        messageId: Long,
        queryId: Long,
        data: Array<ByteArray>,
        botUserId: Int,
        userBot: UserBot?
    ) {

        sudo confirmTo queryId

        if (data.isEmpty()) {

            botDeleteMenu(botUserId, userBot, userId, chatId, messageId, isEdit = true, again = false)

        } else when (data[0][0].toInt()) {

            0 -> {

                val oldStatus = (getMessage(
                    chatId,
                    messageId
                ).replyMarkup as TdApi.ReplyMarkupInlineKeyboard).rows[0][1].text == SQUARE_ENABLE

                sudo makeInlineButton deleteButtons(userId, botUserId, true, !oldStatus) at messageId editTo chatId

            }

            1 -> botDeleteMenu(botUserId, userBot, userId, chatId, messageId, isEdit = true, again = true)

            2 -> {

                val L = localeFor(userId)
                val export = (getMessage(
                    chatId,
                    messageId
                ).replyMarkup as TdApi.ReplyMarkupInlineKeyboard).rows[0][1].text == SQUARE_ENABLE

                sudo make Typing sendTo chatId

                val status = sudo make L.STOPPING at messageId syncEditTo chatId

                val bot = launcher.initBot(userBot!!)
                bot.waitForClose()

                if (export) {
                    sudo make L.BACKING_UP editTo status
                    sudo make Typing sendTo chatId

                    val backupFile = backupToFile(bot)

                    sudo make UploadingDocument sendTo chatId
                    val uploaded = sudo make backupFile syncTo chatId

                    deleteUploaded(uploaded)
                    backupFile.delete()

                }

                sudo make L.DELETING editTo status
                sudo make Typing sendTo chatId

                bot.destroy()

                sudo make L.BOT_DELETED editTo status

            }

        }

    }

}