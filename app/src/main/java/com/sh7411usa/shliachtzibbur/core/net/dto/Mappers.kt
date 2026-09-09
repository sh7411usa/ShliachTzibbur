package com.sh7411usa.shliachtzibbur.core.net.dto

import com.sh7411usa.shliachtzibbur.core.model.AddMembersResult
import com.sh7411usa.shliachtzibbur.core.model.AuthChallenge
import com.sh7411usa.shliachtzibbur.core.model.Device
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.GroupKind
import com.sh7411usa.shliachtzibbur.core.model.GroupLimits
import com.sh7411usa.shliachtzibbur.core.model.GroupSettings
import com.sh7411usa.shliachtzibbur.core.model.LegalDocument
import com.sh7411usa.shliachtzibbur.core.model.Member
import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.model.SenderKind
import com.sh7411usa.shliachtzibbur.core.model.User
import com.sh7411usa.shliachtzibbur.core.model.WhoCanAddMembers
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost

fun UserDto.toDomain(): User = User(
    id = id,
    displayName = displayName,
    phoneE164 = phoneE164,
    kind = SenderKind.fromWire(kind),
    createdAt = createdAt,
    email = email,
    googleLinked = googleLinked,
)

fun DeviceDto.toDomain(): Device = Device(
    id = id,
    platform = platform,
    deviceModel = deviceModel,
    registeredAt = registeredAt,
    lastSeenAt = lastSeenAt,
    userId = userId,
)

fun AuthStartResponse.toDomain(): AuthChallenge = AuthChallenge(
    challengeId = challengeId,
    resendAfterSeconds = resendAfterSeconds,
)

fun GroupSettingsDto.toDomain(): GroupSettings = GroupSettings(
    whoCanPost = WhoCanPost.fromWire(whoCanPost),
    whoCanAddMembers = WhoCanAddMembers.fromWire(whoCanAddMembers),
)

fun GroupLimitsDto.toDomain(): GroupLimits = GroupLimits(
    memberCap = memberCap,
    messageMaxLength = messageMaxLength,
    minMembersToPost = minMembersToPost,
)

fun GroupDto.toDomain(): Group = Group(
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
    unreadCount = unreadCount,
    settings = settings.toDomain(),
    limits = limits.toDomain(),
)

fun MemberDto.toDomain(): Member = Member(
    userId = userId,
    displayName = displayName,
    phoneE164 = phoneE164,
    role = Role.fromWire(role),
    kind = SenderKind.fromWire(kind),
    joinedAt = joinedAt,
)

fun AddMembersResponseDto.toDomain(): AddMembersResult = AddMembersResult(
    added = added.map { it.toDomain() },
    notFound = notFound,
    alreadyMember = alreadyMember.map { it.toDomain() },
)

fun MessageDto.toDomain(fallbackGroupId: String? = null): Message = Message(
    id = id,
    groupId = groupId.ifBlank { fallbackGroupId.orEmpty() },
    seq = seq,
    senderId = senderId,
    body = body,
    clientMessageId = clientMessageId,
    createdAt = createdAt,
)

fun LegalDocumentDto.toDomain(): LegalDocument = LegalDocument(
    key = key,
    text = text,
    checksum = checksum,
)
