package com.sh7411usa.shliachtzibbur.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sh7411usa.shliachtzibbur.core.model.Message

@Entity(
    tableName = "messages",
    indices = [Index(value = ["groupId", "seq"], unique = true), Index(value = ["clientMessageId"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val seq: Long,
    val senderId: String?,
    val body: String,
    val clientMessageId: String?,
    val createdAt: String?,
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    groupId = groupId,
    seq = seq,
    senderId = senderId,
    body = body,
    clientMessageId = clientMessageId,
    createdAt = createdAt,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    groupId = groupId,
    seq = seq,
    senderId = senderId,
    body = body,
    clientMessageId = clientMessageId,
    createdAt = createdAt,
)
