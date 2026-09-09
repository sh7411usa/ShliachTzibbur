package com.sh7411usa.shliachtzibbur.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sh7411usa.shliachtzibbur.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY lastMessageSeq = 0, lastActivityAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun observe(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun find(id: String): GroupEntity?

    @Upsert
    suspend fun upsert(groups: List<GroupEntity>)

    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM groups WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<String>)

    @Query("DELETE FROM groups")
    suspend fun deleteAll()

    @Query("UPDATE groups SET lastReadSeq = :seq WHERE id = :id AND lastReadSeq < :seq")
    suspend fun advanceReadSeq(id: String, seq: Long)

    @Query("UPDATE groups SET deliveredSeq = :seq WHERE id = :id AND deliveredSeq < :seq")
    suspend fun advanceDeliveredSeq(id: String, seq: Long)

    @Query(
        "UPDATE groups SET lastMessageSeq = :seq, lastMessagePreview = :preview, " +
            "lastActivityAt = :createdAt WHERE id = :id AND lastMessageSeq < :seq"
    )
    suspend fun updateLastMessage(id: String, seq: Long, preview: String?, createdAt: String?)

    @Transaction
    suspend fun replaceAll(groups: List<GroupEntity>) {
        upsert(groups)
        deleteMissing(groups.map { it.id })
    }
}
