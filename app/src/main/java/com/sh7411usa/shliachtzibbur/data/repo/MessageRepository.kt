package com.sh7411usa.shliachtzibbur.data.repo

import com.sh7411usa.shliachtzibbur.core.crypto.CryptoOutcome
import com.sh7411usa.shliachtzibbur.core.crypto.MessageCrypto
import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.MessageSecurity
import com.sh7411usa.shliachtzibbur.core.model.OutboxState
import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.ErrorType
import com.sh7411usa.shliachtzibbur.core.result.apiCatching
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.util.Ids
import com.sh7411usa.shliachtzibbur.core.util.Log
import com.sh7411usa.shliachtzibbur.core.util.PinControl
import com.sh7411usa.shliachtzibbur.core.util.PollSpec
import com.sh7411usa.shliachtzibbur.core.util.PollToken
import com.sh7411usa.shliachtzibbur.core.util.Reactions
import com.sh7411usa.shliachtzibbur.data.local.dao.GroupDao
import com.sh7411usa.shliachtzibbur.data.local.dao.MessageDao
import com.sh7411usa.shliachtzibbur.data.local.dao.OutboxDao
import com.sh7411usa.shliachtzibbur.data.local.entity.OutboxEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.toDomain
import com.sh7411usa.shliachtzibbur.data.local.entity.toEntity
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCrypto
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import com.sh7411usa.shliachtzibbur.data.prefs.NoEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
    private val crypto: GroupCryptoSource = NoEncryption,
    private val selfUserId: suspend () -> String? = { null },
    /** A send that is neither confirmed nor rejected within this window is marked FAILED. */
    private val sendTimeoutMs: Long = 20_000L,
) {
    private val pageSize = 50

    fun conversation(groupId: String): Flow<List<ConversationItem>> =
        combine(
            messageDao.observeForGroup(groupId),
            outboxDao.observeForGroup(groupId),
            crypto.crypto(groupId),
        ) { messages, outbox, groupCrypto ->
            val deliveredClientIds = messages.mapNotNull { it.clientMessageId }.toSet()
            val delivered = messages.map { entity ->
                val message = entity.toDomain()
                val (security, plaintext) = classify(message, groupCrypto)
                ConversationItem.Delivered(message, security, plaintext)
            }
            val pending = outbox
                .filter { it.clientMessageId !in deliveredClientIds }
                .map { ConversationItem.Pending(it.toDomain()) }
            delivered + pending
        }

    /** Decryption outcome + security label for one stored message. */
    private fun classify(message: Message, gc: GroupCrypto): Pair<MessageSecurity, String?> {
        if (ServiceMessage.parse(message.text) != null) return MessageSecurity.None to null
        return when (val outcome = MessageCrypto.decrypt(message.text, message.seq, message.senderId, gc.keysForDecrypt)) {
            is CryptoOutcome.Decrypted -> MessageSecurity.Secure to outcome.plaintext
            CryptoOutcome.Undecryptable -> MessageSecurity.Undecryptable to null
            CryptoOutcome.Plain ->
                if (gc.enabled && message.seq >= gc.enabledSinceSeq && gc.enabledSinceSeq > 0) {
                    MessageSecurity.Insecure to null
                } else {
                    MessageSecurity.None to null
                }
        }
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
        // An encrypted group with no key on this device must not leak plaintext.
        val gc = crypto.crypto(groupId).first()
        val isControl = ServiceMessage.parse(trimmed) != null || PinControl.parse(trimmed) != null
        if (gc.enabled && gc.activeKey == null && !isControl) {
            return ApiResult.Failure(
                ApiException(ErrorType.ENCRYPTION_LOCKED, status = 0, detail = "No encryption key"),
            )
        }
        // The server rejects bodies over messageMaxLength; check the *wire* body.
        if (projectedWireLength(gc, trimmed, isControl) > MAX_BODY_LENGTH) {
            return ApiResult.Failure(
                ApiException(ErrorType.INVALID_MESSAGE, status = 0, detail = "Message too long"),
            )
        }
        return enqueue(groupId, trimmed)
    }

    private fun projectedWireLength(gc: GroupCrypto, plaintext: String, isControl: Boolean): Int =
        if (gc.enabled && gc.activeKey != null && !isControl && !MessageCrypto.isCipherText(plaintext)) {
            MessageCrypto.projectedCipherLength(plaintext)
        } else {
            plaintext.length
        }

    /** Send an in-band coordination message (encryption on/off, key changed). Always plaintext. */
    suspend fun sendServiceMessage(groupId: String, kind: ServiceMessage): ApiResult<Unit> =
        enqueue(groupId, ServiceMessage.body(kind))

    /** Send a pin / unpin control message. Always plaintext. */
    suspend fun sendPinControl(groupId: String, control: PinControl): ApiResult<Unit> =
        enqueue(groupId, PinControl.body(control))

    /**
     * If an admin turned encryption on for a group that didn't yet have 3 members,
     * announce it now (once membership reaches 3). No-op otherwise.
     */
    suspend fun announcePendingEncryption(groupId: String) {
        val gc = crypto.crypto(groupId).first()
        if (!gc.enabled || !gc.pendingAnnounce) return
        val group = groupDao.find(groupId) ?: return
        if (Role.fromWire(group.role) != Role.ADMIN || group.memberCount < 3) return
        crypto.setEnabled(groupId, enabled = true, pendingAnnounce = false)
        sendServiceMessage(groupId, ServiceMessage.EncryptionOn)
    }

    private suspend fun enqueue(groupId: String, text: String): ApiResult<Unit> {
        val clientMessageId = Ids.newUuid()
        outboxDao.upsert(
            OutboxEntity(
                clientMessageId = clientMessageId,
                groupId = groupId,
                text = text,
                state = OutboxState.SENDING.name,
                createdAtMillis = System.currentTimeMillis(),
                lastError = null,
            ),
        )
        return deliver(groupId, clientMessageId, text)
    }

    suspend fun retry(clientMessageId: String): ApiResult<Unit> {
        val entry = outboxDao.find(clientMessageId) ?: return ApiResult.Success(Unit)
        outboxDao.updateState(clientMessageId, OutboxState.SENDING.name, null)
        return deliver(entry.groupId, clientMessageId, entry.text)
    }

    /** Remove a queued/failed outgoing message the user chose to discard. */
    suspend fun deleteOutbox(clientMessageId: String) {
        outboxDao.delete(clientMessageId)
    }

    suspend fun flushOutbox(): ApiResult<Unit> = apiCatching {
        outboxDao.all()
            .filter { it.state != OutboxState.FAILED.name }
            .forEach { deliver(it.groupId, it.clientMessageId, it.text) }
    }

    /**
     * Reconcile queued rows for a group:
     *  - rows whose message is already stored -> deleted
     *  - rows older than [sendTimeoutMs] and still unconfirmed -> FAILED
     *  - PENDING rows (server accepted but we never saw the message) -> retried
     */
    suspend fun sweepStuckOutbox(groupId: String) {
        val now = System.currentTimeMillis()
        for (row in outboxDao.forGroup(groupId)) {
            if (messageDao.findByClientId(row.clientMessageId) != null) {
                outboxDao.delete(row.clientMessageId)
                continue
            }
            val age = now - row.createdAtMillis
            when {
                row.state == OutboxState.FAILED.name -> Unit
                age > sendTimeoutMs -> {
                    outboxDao.updateState(row.clientMessageId, OutboxState.FAILED.name, ERROR_TIMEOUT)
                    Log.w("Outbox row ${row.clientMessageId} timed out after ${age}ms")
                }
                row.state == OutboxState.PENDING.name ->
                    deliver(row.groupId, row.clientMessageId, row.text)
            }
        }
    }

    private suspend fun deliver(
        groupId: String,
        clientMessageId: String,
        text: String,
    ): ApiResult<Unit> {
        val wireBody = encryptForSend(groupId, clientMessageId, text)
        Log.d("Sending message group=$groupId cmid=$clientMessageId len=${wireBody.length} enc=${wireBody !== text}")
        val outcome = withTimeoutOrNull(sendTimeoutMs) {
            when (val result = apiCatching { api.sendMessage(groupId, clientMessageId, wireBody) }) {
                is ApiResult.Success -> {
                    result.value?.let { persist(groupId, listOf(it)) }
                    // Pull the server's copy so the message (carrying our
                    // clientMessageId) lands and persist() clears the outbox row.
                    refreshLatest(groupId)
                    if (outboxDao.find(clientMessageId) == null ||
                        messageDao.findByClientId(clientMessageId) != null
                    ) {
                        outboxDao.delete(clientMessageId)
                        Log.d("Message $clientMessageId confirmed")
                        ApiResult.Success(Unit)
                    } else {
                        // Server returned 2xx but no visible message yet; the
                        // confirm-sweep will retry/confirm.
                        outboxDao.updateState(clientMessageId, OutboxState.PENDING.name, null)
                        Log.d("Message $clientMessageId accepted, awaiting confirmation")
                        ApiResult.Success(Unit)
                    }
                }

                is ApiResult.Failure -> {
                    if (result.error.type == ErrorType.CLIENT_MESSAGE_ID_REUSED) {
                        outboxDao.delete(clientMessageId)
                        ApiResult.Success(Unit)
                    } else {
                        val reason = buildString {
                            append(result.error.type)
                            if (result.error.status != 0) append(" (${result.error.status})")
                        }
                        outboxDao.updateState(clientMessageId, OutboxState.FAILED.name, reason)
                        Log.w("Message $clientMessageId failed: $reason")
                        result
                    }
                }
            }
        }
        return outcome ?: run {
            outboxDao.updateState(clientMessageId, OutboxState.FAILED.name, ERROR_TIMEOUT)
            Log.w("Message $clientMessageId send timed out")
            ApiResult.Failure(ApiException(ERROR_TIMEOUT, status = 0, detail = "Message send timed out"))
        }
    }

    /**
     * Returns the body to actually put on the wire: the ciphertext when the group
     * is encrypted and a key is available, otherwise [text] unchanged (referential
     * equality signals "not encrypted" to the caller's log line).
     */
    private suspend fun encryptForSend(groupId: String, clientMessageId: String, text: String): String {
        if (ServiceMessage.parse(text) != null || PinControl.parse(text) != null ||
            MessageCrypto.isCipherText(text)
        ) {
            return text
        }
        val gc = crypto.crypto(groupId).first()
        val key = gc.activeKey?.takeIf { gc.enabled } ?: return text
        // The server assigns the real seq; anticipate it and let the receiver's
        // ±seq search close the gap. Space concurrent outbox rows apart to avoid
        // the same sender reusing a nonce.
        val ahead = outboxDao.forGroup(groupId).count {
            it.clientMessageId != clientMessageId && it.state != OutboxState.FAILED.name
        }
        val anticipatedSeq = (messageDao.maxSeq(groupId) ?: 0L) + 1 + ahead
        return MessageCrypto.encrypt(text, anticipatedSeq, selfUserId(), key)
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

        // Learn the group's encryption state from what arrived.
        val gc = crypto.crypto(groupId).first()
        distinct.sortedBy { it.seq }.forEach { m ->
            when {
                ServiceMessage.parse(m.text) == ServiceMessage.EncryptionOn ->
                    crypto.setEnabled(groupId, true, sinceSeq = m.seq)
                ServiceMessage.parse(m.text) == ServiceMessage.EncryptionOff ->
                    crypto.setEnabled(groupId, false)
                MessageCrypto.isCipherText(m.text) && !gc.enabled ->
                    crypto.setEnabled(groupId, true, sinceSeq = m.seq)
                else -> Unit
            }
        }

        // Reactions, service/control and poll-vote messages are shown differently,
        // not as their own row, so they must not become a group's "last message".
        val gcNow = crypto.crypto(groupId).first()
        fun shownText(m: Message): String? = when (
            val outcome = MessageCrypto.decrypt(m.text, m.seq, m.senderId, gcNow.keysForDecrypt)
        ) {
            is CryptoOutcome.Decrypted -> outcome.plaintext
            CryptoOutcome.Undecryptable -> ENCRYPTED_PREVIEW
            CryptoOutcome.Plain -> m.text
        }
        val newestEntry = distinct
            .sortedByDescending { it.seq }
            .firstNotNullOfOrNull { m ->
                val shown = shownText(m) ?: return@firstNotNullOfOrNull null
                when {
                    Reactions.of(shown) != null -> null
                    ServiceMessage.parse(shown) != null || PinControl.parse(shown) != null -> null
                    PollToken.parse(shown) != null -> null
                    PollSpec.isPoll(shown) -> m to ("📊 " + (PollSpec.parse(shown)?.question ?: "")).take(140)
                    else -> m to shown.take(140)
                }
            } ?: return
        groupDao.updateLastMessage(
            id = groupId,
            seq = newestEntry.first.seq,
            preview = newestEntry.second,
            createdAt = newestEntry.first.createdAt,
        )
    }

    /**
     * Full-text-ish search across every cached message. Encrypted bodies are
     * decrypted (with the group's local keys) before matching, so search works
     * inside encrypted groups too; ciphertext that can't be opened is skipped.
     */
    suspend fun search(query: String): List<Message> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val direct = messageDao.search(q).map { it.toDomain() }
        val cryptoByGroup = HashMap<String, GroupCrypto>()
        val fromCipher = messageDao.cipherMessages().mapNotNull { entity ->
            val message = entity.toDomain()
            val gc = cryptoByGroup.getOrPut(message.groupId) { crypto.crypto(message.groupId).first() }
            val plain = (MessageCrypto.decrypt(message.text, message.seq, message.senderId, gc.keysForDecrypt)
                as? CryptoOutcome.Decrypted)?.plaintext ?: return@mapNotNull null
            if (!plain.contains(q, ignoreCase = true)) return@mapNotNull null
            val name = message.displayName
            message.copy(body = if (name.isNullOrBlank()) plain else "$name: $plain")
        }
        return (direct + fromCipher).distinctBy { it.id }
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

    /**
     * For notifications: returns copies of [messages] with encrypted bodies
     * replaced by their plaintext (or a lock placeholder when no key opens them).
     * Service messages and plaintext pass through unchanged.
     */
    suspend fun decryptedForDisplay(groupId: String, messages: List<Message>): List<Message> {
        if (messages.none { MessageCrypto.isCipherText(it.text) }) return messages
        val gc = crypto.crypto(groupId).first()
        return messages.map { m ->
            if (!MessageCrypto.isCipherText(m.text)) return@map m
            val name = m.displayName
            val shown = when (
                val o = MessageCrypto.decrypt(m.text, m.seq, m.senderId, gc.keysForDecrypt)
            ) {
                is CryptoOutcome.Decrypted -> o.plaintext
                else -> ENCRYPTED_PREVIEW
            }
            m.copy(body = if (name.isNullOrBlank()) shown else "$name: $shown")
        }
    }

    companion object {
        const val ERROR_TIMEOUT = "send_timed_out"
        const val ENCRYPTED_PREVIEW = "🔒"
        const val MAX_BODY_LENGTH = 1000
    }
}
