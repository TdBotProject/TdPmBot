package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.sql.Database

interface PmInstance {

    val database: Database

    val messageRecords: MessageRecords

    val messages: MessageRecordDao

}