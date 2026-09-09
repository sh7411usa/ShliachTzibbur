package com.sh7411usa.shliachtzibbur.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sh7411usa.shliachtzibbur.core.model.OutboxMessage
import com.sh7411usa.shliachtzibbur.core.model.OutboxState

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val clientMessageId: String,
    val groupId: String,
    val text: String,
    val state: String,
    val createdAtMillis: Long,
    val lastError: String?,
)

fun OutboxEntity.toDomain(): OutboxMessage = OutboxMessage(
    clientMessageId = clientMessageId,
    groupId = groupId,
    text = text,
    state = runCatching { OutboxState.valueOf(state) }.getOrDefault(OutboxState.PENDING),
    createdAtMillis = createdAtMillis,
    lastError = lastError,
)
