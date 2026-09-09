package com.sh7411usa.shliachtzibbur.data.repo

import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.OutboxState
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.ErrorType
import com.sh7411usa.shliachtzibbur.core.result.apiCatching
import com.sh7411usa.shliachtzibbur.core.util.Ids
import com.sh7411usa.shliachtzibbur.core.util.Log
import com.sh7411usa.shliachtzibbur.data.local.dao.GroupDao
import com.sh7411usa.shliachtzibbur.data.local.dao.MessageDao
import com.sh7411usa.shliachtzibbur.data.local.dao.OutboxDao
import com.sh7411usa.shliachtzibbur.data.local.entity.OutboxEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.toDomain
import com.sh7411usa.shliachtzibbur.data.local.entity.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Message history and sending.
 *
 * Delivery rule from the API: **store first, then ack**. Incoming messages are
 * written to Room by [applyIncoming] before any acknowledgement is sent.
 *
 * Sending is durable: [send] writes an [OutboxEntity] before the network call so
 * a queued message survives process death, and reconciles by `clientMessageId`.
 */
class MessageRepository(
    private val api: TzibburApi,
    private val messageDao: MessageDao,
    private val outboxDao: OutboxDao,
    private val groupDao: GroupDao,
) {
    private val pageSize = 50

    fun conversation(groupId: String): Flow<List<ConversationItem>> =
        combine(
            messageDao.observeForGroup(groupId),
            outboxDao.observeForGroup(groupId),
        ) { messages, outbox ->
            val deliveredClientIds = messages.mapNotNull { it.clientMessageId }.toSet()
            val delivered = messages.map { ConversationItem.Delivered(it.toDomain()) }
            val pending = outbox
                .filter { it.clientMessageId !in deliveredClientIds }
                .map { ConversationItem.Pending(it.toDomain()) }
            delivered + pending
        }

    fun unreadCount(groupId: String, afterSeq: Long): Flow<Int> =
        messageDao.observeUnreadCount(groupId, afterSeq)

    /** Fetch the newest messages for a group (used on open and on manual refresh). */
    suspend fun refreshLatest(groupId: String): ApiResult<Unit> = apiCatching {
        val fromSeq = messageDao.maxSeq(groupId)
        val page = api.getMessages(
            groupId = groupId,
            afterSeq = fromSeq,
            beforeSeq = null,
            limit = pageSize,
        )
        persist(groupId, page.items)
    }

    /** Page backwards. Returns true if more history may exist. */
    suspend fun loadOlder(groupId: String): ApiResult<Boolean> = apiCatching {
        val oldest = messageDao.minSeq(groupId)
        if (oldest != null && oldest <= 1L) return@apiCatching false
        val page = api.getMessages(
            groupId = groupId,
            afterSeq = null,
            beforeSeq = oldest,
            limit = pageSize,
        )
        persist(groupId, page.items)
        page.items.isNotEmpty() && (messageDao.minSeq(groupId) ?: 1L) > 1L
    }

    suspend fun send(groupId: String, text: String): ApiResult<Unit> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ApiResult.Failure(
                ApiException(ErrorType.INVALID_MESSAGE, status = 0, detail = "Empty message"),
            )
        }
        val clientMessageId = Ids.newUuid()
        outboxDao.upsert(
            OutboxEntity(
                clientMessageId = clientMessageId,
                groupId = groupId,
                text = trimmed,
                state = OutboxState.SENDING.name,
                createdAtMillis = System.currentTimeMillis(),
                lastError = null,
            ),
        )
        return deliver(groupId, clientMessageId, trimmed)
    }

    suspend fun retry(clientMessageId: String): ApiResult<Unit> {
        val entry = outboxDao.all().firstOrNull { it.clientMessageId == clientMessageId }
            ?: return ApiResult.Success(Unit)
        outboxDao.updateState(clientMessageId, OutboxState.SENDING.name, null)
        return deliver(entry.groupId, clientMessageId, entry.text)
    }

    suspend fun flushOutbox(): ApiResult<Unit> = apiCatching {
        outboxDao.all()
            .filter { it.state != OutboxState.SENDING.name }
            .forEach { deliver(it.groupId, it.clientMessageId, it.text) }
    }

    private suspend fun deliver(
        groupId: String,
        clientMessageId: String,
        text: String,
    ): ApiResult<Unit> {
        val result = apiCatching { api.sendMessage(groupId, clientMessageId, text) }
        return when (result) {
            is ApiResult.Success -> {
                result.value?.let { persist(groupId, listOf(it)) }
                // If the reply was not a full message object, the outbox row is
                // cleared when the message arrives via history/WebSocket.
                if (result.value != null) outboxDao.delete(clientMessageId)
                else outboxDao.updateState(clientMessageId, OutboxState.PENDING.name, null)
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> {
                if (result.error.type == ErrorType.CLIENT_MESSAGE_ID_REUSED) {
                    // Already accepted on a previous attempt.
                    outboxDao.delete(clientMessageId)
                    ApiResult.Success(Unit)
                } else {
                    outboxDao.updateState(
                        clientMessageId,
                        OutboxState.FAILED.name,
                        result.error.type,
                    )
                    result
                }
            }
        }
    }

    /** Store incoming messages. Safe to call from sync paths; does not ack. */
    suspend fun applyIncoming(groupId: String, messages: List<Message>) {
        persist(groupId, messages)
    }

    private suspend fun persist(groupId: String, messages: List<Message>) {
        if (messages.isEmpty()) return
        val distinct = messages.distinctBy { it.id }.filter { it.groupId == groupId || it.groupId.isBlank() }
        messageDao.upsert(distinct.map { it.copy(groupId = groupId).toEntity() })
        distinct.mapNotNull { it.clientMessageId }.forEach { outboxDao.delete(it) }
        val newest = distinct.maxByOrNull { it.seq } ?: return
        groupDao.updateLastMessage(
            id = groupId,
            seq = newest.seq,
            preview = newest.text.take(140),
            createdAt = newest.createdAt,
        )
    }

    suspend fun markRead(groupId: String, seq: Long) {
        groupDao.advanceReadSeq(groupId, seq)
    }

    suspend fun lastReadSeq(groupId: String): Long = groupDao.find(groupId)?.lastReadSeq ?: 0L

    /** Acknowledge delivery up to [seq] (advances the device's server-side deliveredSeq). */
    suspend fun ackDelivery(groupId: String, seq: Long): ApiResult<Unit> = apiCatching {
        api.ack(groupId, seq)
        groupDao.advanceDeliveredSeq(groupId, seq)
    }
}
