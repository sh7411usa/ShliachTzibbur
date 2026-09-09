package com.sh7411usa.shliachtzibbur.data.local.entity

import androidx.room.Entity
import com.sh7411usa.shliachtzibbur.core.model.Member
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.model.SenderKind

@Entity(tableName = "members", primaryKeys = ["groupId", "userId"])
data class MemberEntity(
    val groupId: String,
    val userId: String,
    val displayName: String,
    val phoneE164: String?,
    val role: String,
    val kind: String,
    val joinedAt: String?,
)

fun MemberEntity.toDomain(): Member = Member(
    userId = userId,
    displayName = displayName,
    phoneE164 = phoneE164,
    role = Role.fromWire(role),
    kind = SenderKind.fromWire(kind),
    joinedAt = joinedAt,
)

fun Member.toEntity(groupId: String): MemberEntity = MemberEntity(
    groupId = groupId,
    userId = userId,
    displayName = displayName,
    phoneE164 = phoneE164,
    role = role.wire,
    kind = kind.wire,
    joinedAt = joinedAt,
)
