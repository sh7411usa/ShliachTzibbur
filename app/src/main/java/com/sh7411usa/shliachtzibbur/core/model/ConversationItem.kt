package com.sh7411usa.shliachtzibbur.core.model

/** A row in a conversation: either a delivered [Message] or a not-yet-confirmed outgoing message. */
sealed interface ConversationItem {
    val sortKey: Long

    data class Delivered(val message: Message) : ConversationItem {
        override val sortKey: Long get() = message.seq
    }

    data class Pending(val outbox: OutboxMessage) : ConversationItem {
        // Pending items always sort after every delivered message.
        override val sortKey: Long get() = Long.MAX_VALUE
    }
}
