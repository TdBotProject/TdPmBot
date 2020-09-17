package io.nekohasekai.pm

import io.nekohasekai.ktlib.td.core.TdHandler
import io.nekohasekai.pm.instance.PmBot

const val PERSIST_BOT_CREATE = 0
const val PERSIST_SET_START_MESSAGES = 2
const val PERSIST_NEW_FUNCTION = 3
const val PERSIST_UNDER_FUNCTION = 4
const val PERSIST_EDIT_FUNCTION = 5

const val DATA_SWITCH_LOCALE = 0L
const val DATA_SET_START_INTEGRATION = 1L
const val DATA_EDIT_BOTS = 2L
const val DATA_EDIT_BOT = 3L
const val DATA_EDIT_STARTS_MESSAGES = 4L
const val DATA_DELETE_BOT_MENU = 5L
const val DATA_DELETE_MESSAGE = 6L
const val DATA_EDIT_OPTIONS = 7L
const val DATA_EDIT_COMMANDS = 8L
const val DATA_EDIT_COMMAND = 9L

val TdHandler.launcher
    get() = when (val sudo = sudo) {
        is TdPmBot -> sudo
        is PmBot -> sudo.launcher
        else -> error("invalid handler")
    }