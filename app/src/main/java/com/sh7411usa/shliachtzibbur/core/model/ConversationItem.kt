package com.sh7411usa.shliachtzibbur.core.model

/** Encryption status of a delivered message, as resolved against the local keys. */
enum class MessageSecurity {
    /** Plaintext in a non-encrypted group (or a service message). */
    None,

    /** Was encrypted and decrypted successfully with a known key. */
    Secure,

    /** Plaintext received in a group that has encryption turned on. */
    Insecure,

    /** Encrypted, but no known key / sequence offset could open it. */
    Undecryptable,
}

/** A row in a conversation: either a delivered [Message] or a not-yet-confirmed outgoing message. */
sealed interface ConversationItem {
    val sortKey: Long

    data class Delivered(
        val message: Message,
        val security: MessageSecurity = MessageSecurity.None,
        /** Decrypted body when [security] is [MessageSecurity.Secure], else null. */
        val plaintext: String? = null,
    ) : ConversationItem {
        override val sortKey: Long get() = message.seq

        /** What the UI should show and parse (replies, reactions, links): decrypted text if available. */
        val displayText: String get() = plaintext ?: message.text
    }

    data class Pending(val outbox: OutboxMessage) : ConversationItem {
        // Pending items always sort after every delivered message.
        override val sortKey: Long get() = Long.MAX_VALUE
    }
}
