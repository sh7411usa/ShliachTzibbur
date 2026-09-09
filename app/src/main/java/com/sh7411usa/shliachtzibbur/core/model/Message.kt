package com.sh7411usa.shliachtzibbur.core.model

/**
 * A delivered message. [seq] is the per-group sequence starting at 1.
 *
 * The server prefixes [body] with the sender's display name and ": ". [displayName]
 * and [text] expose that split for rendering while [body] keeps the raw value.
 */
data class Message(
    val id: String,
    val groupId: String,
    val seq: Long,
    val senderId: String?,
    val body: String,
    val clientMessageId: String?,
    val createdAt: String?,
) {
    private val splitIndex: Int get() = body.indexOf(": ")

    val displayName: String?
        get() = if (splitIndex > 0) body.substring(0, splitIndex) else null

    val text: String
        get() = if (splitIndex > 0) body.substring(splitIndex + 2) else body
}

enum class OutboxState { PENDING, SENDING, FAILED }

/**
 * A locally queued outgoing message. Written before the network call so a send
 * survives process death; reconciled against the real [Message] by [clientMessageId].
 */
data class OutboxMessage(
    val clientMessageId: String,
    val groupId: String,
    val text: String,
    val state: OutboxState,
    val createdAtMillis: Long,
    val lastError: String?,
)
