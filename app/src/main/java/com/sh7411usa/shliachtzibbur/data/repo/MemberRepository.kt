package com.sh7411usa.shliachtzibbur.data.repo

import com.sh7411usa.shliachtzibbur.core.model.AddMembersResult
import com.sh7411usa.shliachtzibbur.core.model.Member
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.apiCatching
import com.sh7411usa.shliachtzibbur.data.local.dao.MemberDao
import com.sh7411usa.shliachtzibbur.data.local.entity.toDomain
import com.sh7411usa.shliachtzibbur.data.local.entity.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Group membership. The member list is cached in Room per group. */
class MemberRepository(
    private val api: TzibburApi,
    private val memberDao: MemberDao,
) {
    fun members(groupId: String): Flow<List<Member>> =
        memberDao.observeForGroup(groupId).map { list -> list.map { it.toDomain() } }

    suspend fun refresh(groupId: String): ApiResult<Unit> = apiCatching {
        val all = mutableListOf<Member>()
        var cursor: String? = null
        do {
            val page = api.listMembers(groupId, cursor, limit = 100)
            all += page.items
            cursor = page.nextCursor
        } while (cursor != null)
        memberDao.replaceForGroup(groupId, all.map { it.toEntity(groupId) })
    }

    suspend fun add(groupId: String, phones: List<String>, region: String?): ApiResult<AddMembersResult> =
        apiCatching {
            val result = api.addMembers(groupId, phones, region)
            if (result.added.isNotEmpty()) {
                memberDao.upsert(result.added.map { it.toEntity(groupId) })
            }
            result
        }

    suspend fun changeRole(groupId: String, userId: String, role: Role): ApiResult<Unit> = apiCatching {
        api.changeRole(groupId, userId, role)
        refresh(groupId)
        Unit
    }

    suspend fun remove(groupId: String, userId: String): ApiResult<Unit> = apiCatching {
        api.removeMember(groupId, userId)
        memberDao.delete(groupId, userId)
    }

    suspend fun checkRegistered(phones: List<String>, region: String?): ApiResult<List<String>> =
        apiCatching { api.checkContacts(phones, region) }
}
