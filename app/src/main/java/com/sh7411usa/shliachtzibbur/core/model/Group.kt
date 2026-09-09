package com.sh7411usa.shliachtzibbur.core.model

/** Server-controlled per-group limits. Defaults match the values documented in the API reference. */
data class GroupLimits(
    val memberCap: Int = 100,
    val messageMaxLength: Int = 1000,
    val minMembersToPost: Int = 3,
)

data class GroupSettings(
    val whoCanPost: WhoCanPost = WhoCanPost.EVERYONE,
    val whoCanAddMembers: WhoCanAddMembers = WhoCanAddMembers.ADMINS,
)

/**
 * A messaging group.
 *
 * [readSeq]/[unreadCount] come from the server but the API notes that no known
 * endpoint advances them, so the UI relies on locally tracked read state
 * (see `MessageRepository`) and treats these as an initial hint only.
 */
data class Group(
    val id: String,
    val name: String,
    val category: String,
    val kind: GroupKind,
    val createdBy: String?,
    val createdAt: String?,
    val role: Role,
    val memberCount: Int,
    val muted: Boolean,
    val readSeq: Long,
    val unreadCount: Int,
    val settings: GroupSettings,
    val limits: GroupLimits,
    val lastMessagePreview: String? = null,
    val lastActivityAt: String? = null,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
    val isSystem: Boolean get() = kind == GroupKind.SYSTEM
    fun canPost(currentUserIsAdmin: Boolean): Boolean =
        !isSystem && (settings.whoCanPost == WhoCanPost.EVERYONE || currentUserIsAdmin)
}
