package io.github.nekohasekai.pm.database

import io.github.nekohasekai.nekolib.i18n.LocaleController

interface PmInstance {

    val L: LocaleController
    val messageRecords: MessageRecords
    val messages: MessageRecordDao

}