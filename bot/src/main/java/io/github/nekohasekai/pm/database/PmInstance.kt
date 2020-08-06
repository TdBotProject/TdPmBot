package io.github.nekohasekai.pm.database

interface PmInstance {

    val messageRecords: MessageRecords
    val messages: MessageRecordDao

}