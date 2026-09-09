package com.sh7411usa.shliachtzibbur.core.model

/** A cursor-paged slice of results (`{ items, nextCursor }` in the API). */
data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
) {
    val hasMore: Boolean get() = nextCursor != null
}

/** A seq-paged slice of messages (`{ items, nextAfterSeq, nextBeforeSeq }`). */
data class MessagePage(
    val items: List<Message>,
    val nextAfterSeq: Long?,
    val nextBeforeSeq: Long?,
)
