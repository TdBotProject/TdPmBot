package io.github.nekohasekai.pm.manage.menu

import io.github.nekohasekai.nekolib.core.utils.*
import io.github.nekohasekai.nekolib.i18n.BACK_ARROW
import io.github.nekohasekai.nekolib.i18n.DISABLED
import io.github.nekohasekai.nekolib.i18n.ENABLED
import io.github.nekohasekai.nekolib.i18n.LocaleController
import io.github.nekohasekai.pm.*
import io.github.nekohasekai.pm.database.BotSetting
import io.github.nekohasekai.pm.database.UserBot
import io.github.nekohasekai.pm.manage.BotHandler
import io.github.nekohasekai.pm.manage.MyBots
import td.TdApi

class PreferencesMenu : BotHandler() {

    companion object {

        const val dataId = DATA_EDIT_OPTIONS

    }

    override fun onLoad() {

        initData(dataId)

    }

    fun optionsButtons(L: LocaleController, botSetting: BotSetting?, botUserId: Int): TdApi.ReplyMarkupInlineKeyboard {

        fun Boolean?.toBlock() = if (this == true) "■" else "□"

        val botId = botUserId.toByteArray()

        return inlineButton {

            newLine {

                textButton(L.OPTION_KEEP_ACTION_MESSAGES)

                dataButton(botSetting?.keepActionMessages.toBlock(), dataId, botId, byteArrayOf(0))

            }

            newLine {

                textButton(L.OPTION_TWO_WAY_SYNC)

                dataButton(botSetting?.twoWaySync.toBlock(), dataId, botId, byteArrayOf(1))

            }

            newLine {

                textButton(L.OPTION_KEEP_REPLY)

                dataButton(botSetting?.keepReply.toBlock(), dataId, botId, byteArrayOf(2))

            }

            newLine {

                textButton(L.OPTION_IGNORE_DELETE_ACTION)

                dataButton(botSetting?.ignoreDeleteAction.toBlock(), dataId, botId, byteArrayOf(3))

            }

            dataLine(L.BACK_ARROW, BotMenu.dataId, botId)

        }

    }

    fun optionsMenu(botUserId: Int, userBot: UserBot?, userId: Int, chatId: Long, messageId: Long, isEdit: Boolean) {

        val botSetting = BotSetting.Cache.fetch(botUserId).value

        val L = LocaleController.forChat(userId)

        sudo makeHtml L.OPTIONS_HELP.input(
                botNameHtml(botUserId, userBot),
                botUserName(botUserId, userBot)
        ) withMarkup optionsButtons(L, botSetting, botUserId) onSuccess {

            if (!isEdit) findHandler<MyBots>().saveActionMessage(userId, it.id)

        } at messageId edit isEdit sendOrEditTo chatId

    }

    override suspend fun onNewBotCallbackQuery(userId: Int, chatId: Long, messageId: Long, queryId: Long, data: Array<ByteArray>, botUserId: Int, userBot: UserBot?) {

        val L = LocaleController.forChat(userId)

        if (data.isEmpty()) {

            optionsMenu(botUserId, userBot, userId, chatId, messageId, true)

            return

        }

        val botSettingCache = BotSetting.Cache.fetch(botUserId)
        val botSetting = botSettingCache.value

        var target = false

        if (botSetting == null) target = true

        when (data[0][0].toInt()) {

            0 -> {

                if (botSetting == null) {

                    botSettingCache.value = database.write {

                        BotSetting.new(botUserId) {

                            keepActionMessages = target

                        }

                    }

                } else {

                    target = !botSetting.keepActionMessages

                    database.write {

                        botSetting.keepActionMessages = target

                        botSetting.flush()

                    }

                }

            }

            1 -> {

                if (botSetting == null) {

                    botSettingCache.value = database.write {

                        BotSetting.new(botUserId) {

                            twoWaySync = target

                        }

                    }

                } else {

                    target = !botSetting.twoWaySync

                    database.write {

                        botSetting.twoWaySync = target

                        botSetting.flush()

                    }

                }

            }

            2 -> {

                if (botSetting == null) {

                    botSettingCache.value = database.write {

                        BotSetting.new(botUserId) {

                            keepReply = target

                        }

                    }

                } else {

                    target = !botSetting.keepReply

                    database.write {

                        botSetting.keepReply = target

                        botSetting.flush()

                    }

                }

            }

            3 -> {

                if (botSetting == null) {

                    botSettingCache.value = database.write {

                        BotSetting.new(botUserId) {

                            ignoreDeleteAction = target

                        }

                    }

                } else {

                    target = !botSetting.ignoreDeleteAction

                    database.write {

                        botSetting.ignoreDeleteAction = target

                        botSetting.flush()


                    }

                }

            }

        }

        sudo makeAnswer (if (!target) L.DISABLED else L.ENABLED) answerTo queryId

        sudo makeInlineButton optionsButtons(L, botSetting, botUserId) at messageId editTo chatId

    }

}