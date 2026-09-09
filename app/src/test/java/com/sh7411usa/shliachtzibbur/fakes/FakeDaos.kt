package com.sh7411usa.shliachtzibbur.fakes

import com.sh7411usa.shliachtzibbur.data.local.dao.GroupDao
import com.sh7411usa.shliachtzibbur.data.local.dao.MessageDao
import com.sh7411usa.shliachtzibbur.data.local.dao.OutboxDao
import com.sh7411usa.shliachtzibbur.data.local.entity.GroupEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.MessageEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fakes for repository unit tests. Only the behaviour used by the repos is faithful. */

class FakeMessageDao : MessageDao {
    val rows = MutableStateFlow<List<MessageEntity>>(emptyList())

    override fun observeForGroup(groupId: String): Flow<List<MessageEntity>> =
        rows.map { list -> list.filter { it.groupId == groupId }.sortedBy { it.seq } }

    override suspend fun latest(groupId: String): MessageEntity? =
        rows.value.filter { it.groupId == groupId }.maxByOrNull { it.seq }

    override suspend fun minSeq(groupId: String): Long? =
        rows.value.filter { it.groupId == groupId }.minOfOrNull { it.seq }

    override suspend fun maxSeq(groupId: String): Long? =
        rows.value.filter { it.groupId == groupId }.maxOfOrNull { it.seq }

    override suspend fun findByClientId(clientMessageId: String): MessageEntity? =
        rows.value.firstOrNull { it.clientMessageId == clientMessageId }

    override fun observeUnreadCount(groupId: String, afterSeq: Long): Flow<Int> =
        rows.map { list -> list.count { it.groupId == groupId && it.seq > afterSeq } }

    override suspend fun upsert(messages: List<MessageEntity>) {
        val byId = rows.value.associateBy { it.id }.toMutableMap()
        messages.forEach { byId[it.id] = it }
        rows.value = byId.values.toList()
    }

    override suspend fun deleteForGroup(groupId: String) {
        rows.value = rows.value.filterNot { it.groupId == groupId }
    }
}

class FakeOutboxDao : OutboxDao {
    val rows = MutableStateFlow<List<OutboxEntity>>(emptyList())

    override fun observeForGroup(groupId: String): Flow<List<OutboxEntity>> =
        rows.map { list -> list.filter { it.groupId == groupId }.sortedBy { it.createdAtMillis } }

    override suspend fun all(): List<OutboxEntity> = rows.value.sortedBy { it.createdAtMillis }

    override suspend fun forGroup(groupId: String): List<OutboxEntity> =
        rows.value.filter { it.groupId == groupId }.sortedBy { it.createdAtMillis }

    override suspend fun find(id: String): OutboxEntity? =
        rows.value.firstOrNull { it.clientMessageId == id }

    override suspend fun upsert(entry: OutboxEntity) {
        rows.value = rows.value.filterNot { it.clientMessageId == entry.clientMessageId } + entry
    }

    override suspend fun updateState(id: String, state: String, error: String?) {
        rows.value = rows.value.map {
            if (it.clientMessageId == id) it.copy(state = state, lastError = error) else it
        }
    }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.clientMessageId == id }
    }

    override suspend fun deleteForGroup(groupId: String) {
        rows.value = rows.value.filterNot { it.groupId == groupId }
    }
}

class FakeGroupDao : GroupDao {
    val rows = MutableStateFlow<List<GroupEntity>>(emptyList())

    override fun observeAll(): Flow<List<GroupEntity>> = rows
    override fun observe(id: String): Flow<GroupEntity?> = rows.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun find(id: String): GroupEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(groups: List<GroupEntity>) {
        val byId = rows.value.associateBy { it.id }.toMutableMap()
        groups.forEach { byId[it.id] = it }
        rows.value = byId.values.toList()
    }

    override suspend fun upsert(group: GroupEntity) = upsert(listOf(group))

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteMissing(keepIds: List<String>) {
        rows.value = rows.value.filter { it.id in keepIds }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun advanceReadSeq(id: String, seq: Long) {
        rows.value = rows.value.map { if (it.id == id && it.lastReadSeq < seq) it.copy(lastReadSeq = seq) else it }
    }

    override suspend fun advanceDeliveredSeq(id: String, seq: Long) {
        rows.value = rows.value.map { if (it.id == id && it.deliveredSeq < seq) it.copy(deliveredSeq = seq) else it }
    }

    override suspend fun updateLastMessage(id: String, seq: Long, preview: String?, createdAt: String?) {
        rows.value = rows.value.map {
            if (it.id == id && it.lastMessageSeq < seq) {
                it.copy(lastMessageSeq = seq, lastMessagePreview = preview, lastActivityAt = createdAt)
            } else {
                it
            }
        }
    }
}
