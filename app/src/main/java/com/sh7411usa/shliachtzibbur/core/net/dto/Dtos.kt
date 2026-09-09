package com.sh7411usa.shliachtzibbur.core.net.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Wire DTOs for the Tzibbur REST API. These mirror the JSON exactly; conversion
 * to domain models lives in Mappers.kt. `explicitNulls = false` and
 * `ignoreUnknownKeys = true` are set on the Json instance, so optional fields are
 * nullable with defaults and new server fields are tolerated.
 */

// ---- Auth ----

@Serializable
data class AuthStartRequest(
    val phone: String,
    val displayName: String? = null,
    val region: String? = null,
    val platform: String,
    val deviceModel: String,
)

@Serializable
data class AuthStartResponse(
    val challengeId: String,
    val resendAfterSeconds: Int = 30,
)

@Serializable
data class AuthVerifyRequest(
    val challengeId: String,
    val code: String,
    val phone: String,
    val displayName: String? = null,
    val region: String? = null,
    val platform: String,
    val deviceModel: String,
)

@Serializable
data class AuthVerifyResponse(
    val user: UserDto,
    val device: DeviceDto,
    val token: String,
)

// ---- Profile ----

@Serializable
data class UserDto(
    val id: String,
    val displayName: String,
    val phoneE164: String? = null,
    val kind: String? = null,
    val createdAt: String? = null,
    val email: String? = null,
    val googleLinked: Boolean = false,
)

@Serializable
data class DeviceDto(
    val id: String,
    val platform: String = "",
    val deviceModel: String = "",
    val registeredAt: String? = null,
    val lastSeenAt: String? = null,
    val userId: String? = null,
)

@Serializable
data class DeviceListDto(val items: List<DeviceDto> = emptyList())

@Serializable
data class UpdateMeRequest(val displayName: String)

// ---- Contacts ----

@Serializable
data class ContactsCheckRequest(val phones: List<String>, val region: String? = null)

@Serializable
data class ContactsCheckResponse(val registered: List<String> = emptyList())

// ---- Legal ----

@Serializable
data class LegalResponse(val document: LegalDocumentDto)

@Serializable
data class LegalDocumentDto(
    val key: String,
    val text: String,
    val checksum: String = "",
)

// ---- Groups ----

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val category: String = "other",
    val kind: String? = null,
    val createdBy: String? = null,
    val createdAt: String? = null,
    val role: String? = null,
    val memberCount: Int = 0,
    val muted: Boolean = false,
    val readSeq: Long = 0,
    val unreadCount: Int = 0,
    val settings: GroupSettingsDto = GroupSettingsDto(),
    val limits: GroupLimitsDto = GroupLimitsDto(),
)

@Serializable
data class GroupSettingsDto(
    val whoCanPost: String? = null,
    val whoCanAddMembers: String? = null,
)

@Serializable
data class GroupLimitsDto(
    val memberCap: Int = 100,
    val messageMaxLength: Int = 1000,
    val minMembersToPost: Int = 3,
)

@Serializable
data class GroupListDto(
    val items: List<GroupDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class GroupCategoriesDto(val categories: List<String> = emptyList())

@Serializable
data class CreateGroupRequest(val name: String, val category: String)

@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val settings: GroupSettingsDto? = null,
)

// ---- Members ----

@Serializable
data class MemberDto(
    val userId: String,
    val displayName: String = "",
    val phoneE164: String? = null,
    val role: String? = null,
    val kind: String? = null,
    val joinedAt: String? = null,
)

@Serializable
data class MemberListDto(
    val items: List<MemberDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class AddMembersRequest(val phones: List<String>, val region: String? = null)

@Serializable
data class AddMembersResponseDto(
    val added: List<MemberDto> = emptyList(),
    val notFound: List<String> = emptyList(),
    val alreadyMember: List<MemberDto> = emptyList(),
)

@Serializable
data class ChangeRoleRequest(val role: String)

// ---- Messages ----

@Serializable
data class MessageDto(
    val id: String,
    val groupId: String = "",
    val seq: Long = 0,
    val senderId: String? = null,
    val body: String = "",
    val clientMessageId: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class MessageListDto(
    val items: List<MessageDto> = emptyList(),
    val nextAfterSeq: Long? = null,
    val nextBeforeSeq: Long? = null,
)

@Serializable
data class SendMessageRequest(val clientMessageId: String, val body: String)

@Serializable
data class AckRequest(val seq: Long)

// ---- Delivery ----

@Serializable
data class PendingResponse(val groups: List<PendingGroupDto> = emptyList())

@Serializable
data class PendingGroupDto(
    val groupId: String,
    val deliveredSeq: Long = 0,
    val hasMore: Boolean = false,
    val messages: List<MessageDto> = emptyList(),
)

// ---- Errors ----

@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int = 0,
    val detail: String? = null,
    val requestId: String? = null,
    val retryAfterSeconds: Int? = null,
    // Either a list of {path, message} or an object {field: reason}; parsed in ErrorParser.
    val errors: JsonElement? = null,
)
