package com.sh7411usa.shliachtzibbur.core.model

/** A Tzibbur user account. `email`/`googleLinked` are surfaced read-only until the API supports them. */
data class User(
    val id: String,
    val displayName: String,
    val phoneE164: String?,
    val kind: SenderKind,
    val createdAt: String?,
    val email: String?,
    val googleLinked: Boolean,
)

/** A registered device (one per auth verification). Holds its own delivery cursor server-side. */
data class Device(
    val id: String,
    val platform: String,
    val deviceModel: String,
    val registeredAt: String?,
    val lastSeenAt: String?,
    val userId: String?,
)
