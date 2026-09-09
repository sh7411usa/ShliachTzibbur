package com.sh7411usa.shliachtzibbur.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sh7411usa.shliachtzibbur.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY role = 'admin' DESC, displayName COLLATE NOCASE ASC")
    fun observeForGroup(groupId: String): Flow<List<MemberEntity>>

    @Upsert
    suspend fun upsert(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)

    @Query("DELETE FROM members WHERE groupId = :groupId AND userId = :userId")
    suspend fun delete(groupId: String, userId: String)

    @Transaction
    suspend fun replaceForGroup(groupId: String, members: List<MemberEntity>) {
        deleteForGroup(groupId)
        upsert(members)
    }
}
