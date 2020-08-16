package io.github.nekohasekai.pm.database

import io.github.nekohasekai.nekolib.core.utils.KeyValueTable

object ActionMessages : KeyValueTable<Int, Long>() {

    val userId = integer("user_id").entityId()

    val messageId = long("message_id")

    override val id = userId

    override val value = messageId

}