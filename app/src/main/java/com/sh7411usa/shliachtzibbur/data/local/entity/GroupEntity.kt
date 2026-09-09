package com.sh7411usa.shliachtzibbur.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.GroupKind
import com.sh7411usa.shliachtzibbur.core.model.GroupLimits
import com.sh7411usa.shliachtzibbur.core.model.GroupSettings
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.model.WhoCanAddMembers
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost

/**
 * Cached group. Server fields are flattened; [lastReadSeq] and [deliveredSeq] are
 * local-only cursors (the API exposes no endpoint that writes read state).
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val kind: String,
    val createdBy: String?,
    val createdAt: String?,
    val role: String,
    val memberCount: Int,
    val readSeq: Long,
    val unreadCountHint: Int,
    val whoCanPost: String,
    val whoCanAddMembers: String,
    val memberCap: Int,
    val messageMaxLength: Int,
    val minMembersToPost: Int,
    val lastReadSeq: Long = 0,
    val deliveredSeq: Long = 0,
    val lastMessageSeq: Long = 0,
    val lastMessagePreview: String? = null,
    val lastActivityAt: String? = null,
)

fun GroupEntity.toDomain(muted: Boolean): Group = Group(
    id = id,
    name = name,
    category = category,
    kind = GroupKind.fromWire(kind),
    createdBy = createdBy,
    createdAt = createdAt,
    role = Role.fromWire(role),
    memberCount = memberCount,
    muted = muted,
    readSeq = readSeq,
    // Unread is tracked locally: highest message seq we know about minus the
    // highest seq the user has viewed. The server's own unreadCount never
    // decreases, so it can't be used directly.
    unreadCount = (lastMessageSeq - lastReadSeq).coerceAtLeast(0).toInt(),
    settings = GroupSettings(WhoCanPost.fromWire(whoCanPost), WhoCanAddMembers.fromWire(whoCanAddMembers)),
    limits = GroupLimits(memberCap, messageMaxLength, minMembersToPost),
    lastMessagePreview = lastMessagePreview,
    lastActivityAt = lastActivityAt,
)

fun Group.toEntity(existing: GroupEntity? = null): GroupEntity = GroupEntity(
    id = id,
    name = name,
    category = category,
    kind = kind.wire,
    createdBy = createdBy,
    createdAt = createdAt,
    role = role.wire,
    memberCount = memberCount,
    readSeq = readSeq,
    unreadCountHint = unreadCount,
    whoCanPost = settings.whoCanPost.wire,
    whoCanAddMembers = settings.whoCanAddMembers.wire,
    memberCap = limits.memberCap,
    messageMaxLength = limits.messageMaxLength,
    minMembersToPost = limits.minMembersToPost,
    // First sync: seed read state from the server so a group with a real
    // unread count shows it. Later syncs keep the locally-advanced values.
    lastReadSeq = existing?.lastReadSeq ?: readSeq,
    deliveredSeq = existing?.deliveredSeq ?: 0,
    lastMessageSeq = maxOf(existing?.lastMessageSeq ?: 0L, readSeq + unreadCount.toLong()),
    lastMessagePreview = existing?.lastMessagePreview,
    lastActivityAt = existing?.lastActivityAt ?: createdAt,
)
