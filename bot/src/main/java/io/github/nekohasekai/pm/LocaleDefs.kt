package io.github.nekohasekai.pm

import io.github.nekohasekai.nekolib.i18n.LocaleController

private val LocaleController.pm by LocaleController.receiveLocaleSet("pm")
private val string = LocaleController.receiveLocaleString { pm }

internal val LocaleController.HELP_MSG by string
internal val LocaleController.PRIVATE_INSTANCE by string

internal val LocaleController.CREATE_BOT_DEF by string
internal val LocaleController.INPUT_BOT_TOKEN by string
internal val LocaleController.INVALID_BOT_TOKEN by string
internal val LocaleController.FETCHING_INFO by string
internal val LocaleController.CREATING_BOT by string
internal val LocaleController.ALREADY_EXISTS by string
internal val LocaleController.FINISH_CREATION by string
internal val LocaleController.CREATE_FINISHED by string

internal val LocaleController.DELETE_BOT_DEF by string
internal val LocaleController.NO_BOTS by string
internal val LocaleController.SELECT_TO_DELETE by string
internal val LocaleController.BOT_SELECTED by string
internal val LocaleController.INVALID_SELECTED by string
internal val LocaleController.DELETE_CONFIRM by string
internal val LocaleController.DELETE_CONFIRM_REGEX by string
internal val LocaleController.CONFIRM_NOT_MATCH by string
internal val LocaleController.STOPPING by string
internal val LocaleController.DELETING by string
internal val LocaleController.BOT_DELETED by string
internal val LocaleController.BOT_AUTH_FAILED by string
internal val LocaleController.BOT_LOGOUT by string

internal val LocaleController.SET_STARTS_DEF by string
internal val LocaleController.SELECT_TO_SET by string
internal val LocaleController.JUMP_TO_SET by string
internal val LocaleController.SET_MESSAGES_STATUS by string
internal val LocaleController.SETTING_UNDEF by string
internal val LocaleController.EMPTY by string
internal val LocaleController.MESSAGES_STATUS_COUNT by string
internal val LocaleController.INPUT_MESSAGES by string
internal val LocaleController.MESSAGE_ADDED by string
internal val LocaleController.MESSAGE_ADDED_FWD by string
internal val LocaleController.MESSAGES_RESET by string
internal val LocaleController.ERROR_IN_PREVIEW by string

internal val LocaleController.SET_INTEGRATION_DEF by string
internal val LocaleController.SET_INTEGRATION by string
internal val LocaleController.INTEGRATION_HAS_SET by string
internal val LocaleController.INTEGRATION_ADMIN_ONLY by string
internal val LocaleController.INTEGRATION_UNDEF by string
internal val LocaleController.INTEGRATION_OK by string
internal val LocaleController.INTEGRATION_PAUSED by string
internal val LocaleController.INTEGRATION_PAUSED_NOTICE by string
internal val LocaleController.INTEGRATION_UNABLE_TO_RESUME by string
internal val LocaleController.INTEGRATION_SET by string
internal val LocaleController.INTEGRATION_PAUSE by string
internal val LocaleController.INTEGRATION_RESUME by string
internal val LocaleController.INTEGRATION_DEL by string
internal val LocaleController.INTEGRATION_ADMIN_ONLY_ENABLED by string
internal val LocaleController.INTEGRATION_ADMIN_ONLY_DISABLED by string


internal val LocaleController.INPUT_NOTICE by string
internal val LocaleController.BANDED_BY by string
internal val LocaleController.JOINED_NOTICE by string
internal val LocaleController.JOIN_NON_PM by string
internal val LocaleController.EXITED by string
internal val LocaleController.NOTHING_TO_EXIT by string
internal val LocaleController.SENT by string
internal val LocaleController.REPLIED by string
internal val LocaleController.EDITED by string
internal val LocaleController.DELETED by string
internal val LocaleController.RECORD_NF by string
internal val LocaleController.REPLIED_NF by string
internal val LocaleController.MESSAGE_DELETED by string
internal val LocaleController.MESSAGE_EDITED by string
internal val LocaleController.DEFAULT_WELCOME by string
internal val LocaleController.POWERED_BY by string

internal val LocaleController.PM_HELP by string