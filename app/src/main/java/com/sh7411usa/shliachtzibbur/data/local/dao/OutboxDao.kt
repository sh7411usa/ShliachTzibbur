package com.sh7411usa.shliachtzibbur.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sh7411usa.shliachtzibbur.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox WHERE groupId = :groupId ORDER BY createdAtMillis ASC")
    fun observeForGroup(groupId: String): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY createdAtMillis ASC")
    suspend fun all(): List<OutboxEntity>

    @Upsert
    suspend fun upsert(entry: OutboxEntity)

    @Query("UPDATE outbox SET state = :state, lastError = :error WHERE clientMessageId = :id")
    suspend fun updateState(id: String, state: String, error: String?)

    @Query("DELETE FROM outbox WHERE clientMessageId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outbox WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
