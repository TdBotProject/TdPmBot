package io.nekohasekai.pm.database

import io.nekohasekai.ktlib.db.KeyValueTable

object ActionMessages : KeyValueTable<Int, Long>("action_messages") {

    val userId = integer("user_id").entityId()

    val messageId = long("message_id")

    val createAt = integer("create_at").default(0).index()

    override val id = userId

    override val value = messageId

}