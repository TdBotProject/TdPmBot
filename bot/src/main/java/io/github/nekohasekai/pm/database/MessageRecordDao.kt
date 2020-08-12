package io.github.nekohasekai.pm.database

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow

class MessageRecordDao(private val records: MessageRecords) : EntityClass<Long, MessageRecord>(records, MessageRecord::class.java) {

    override fun createInstance(entityId: EntityID<Long>, row: ResultRow?) = MessageRecord(records, entityId)

}