package com.sh7411usa.shliachtzibbur.core.model

/** A member of a group, as returned by `GET /v1/groups/{id}/members`. */
data class Member(
    val userId: String,
    val displayName: String,
    val phoneE164: String?,
    val role: Role,
    val kind: SenderKind,
    val joinedAt: String?,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}

/** Result of `POST /v1/groups/{id}/members`. */
data class AddMembersResult(
    val added: List<Member>,
    val notFound: List<String>,
    val alreadyMember: List<Member>,
)
