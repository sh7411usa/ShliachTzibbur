package com.sh7411usa.shliachtzibbur.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sh7411usa.shliachtzibbur.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY seq ASC")
    fun observeForGroup(groupId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY seq DESC LIMIT 1")
    suspend fun latest(groupId: String): MessageEntity?

    @Query("SELECT MIN(seq) FROM messages WHERE groupId = :groupId")
    suspend fun minSeq(groupId: String): Long?

    @Query("SELECT MAX(seq) FROM messages WHERE groupId = :groupId")
    suspend fun maxSeq(groupId: String): Long?

    @Query("SELECT * FROM messages WHERE clientMessageId = :clientMessageId LIMIT 1")
    suspend fun findByClientId(clientMessageId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE groupId = :groupId AND seq > :afterSeq")
    fun observeUnreadCount(groupId: String, afterSeq: Long): Flow<Int>

    @Query(
        "SELECT * FROM messages WHERE body LIKE '%' || :query || '%' " +
            "ORDER BY createdAt DESC, seq DESC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int = 100): List<MessageEntity>

    /** Rows whose body contains a `$E<n>:` encryption token, for decrypt-then-match search. */
    @Query("SELECT * FROM messages WHERE body GLOB '*[\$]E[0-9]:*' ORDER BY createdAt DESC, seq DESC LIMIT :limit")
    suspend fun cipherMessages(limit: Int = 200): List<MessageEntity>

    @Upsert
    suspend fun upsert(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
