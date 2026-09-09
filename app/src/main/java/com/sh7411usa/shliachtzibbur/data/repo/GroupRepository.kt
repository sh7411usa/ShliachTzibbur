package com.sh7411usa.shliachtzibbur.data.repo

import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.apiCatching
import com.sh7411usa.shliachtzibbur.core.util.Log
import com.sh7411usa.shliachtzibbur.data.local.dao.GroupDao
import com.sh7411usa.shliachtzibbur.data.local.entity.toDomain
import com.sh7411usa.shliachtzibbur.data.local.entity.toEntity
import com.sh7411usa.shliachtzibbur.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Groups, backed by Room so the list is available offline. The server's `muted`
 * flag is not writable through the API, so mute state is kept locally in
 * [SettingsStore] and merged in here.
 */
class GroupRepository(
    private val api: TzibburApi,
    private val groupDao: GroupDao,
    private val settingsStore: SettingsStore,
) {
    private var cachedCategories: List<String>? = null

    val groups: Flow<List<Group>> =
        combine(groupDao.observeAll(), settingsStore.settings) { entities, settings ->
            entities.map { it.toDomain(muted = it.id in settings.mutedGroupIds) }
        }

    fun group(id: String): Flow<Group?> =
        combine(groupDao.observe(id), settingsStore.settings) { entity, settings ->
            entity?.toDomain(muted = entity.id in settings.mutedGroupIds)
        }

    suspend fun refresh(): ApiResult<Unit> = apiCatching {
        val all = mutableListOf<Group>()
        var cursor: String? = null
        do {
            val page = api.listGroups(cursor = cursor, limit = 100)
            all += page.items
            cursor = page.nextCursor
        } while (cursor != null)

        val existing = all.associate { it.id to groupDao.find(it.id) }
        groupDao.replaceAll(all.map { it.toEntity(existing[it.id]) })
    }

    suspend fun refreshGroup(id: String): ApiResult<Group> = apiCatching {
        val group = api.getGroup(id)
        groupDao.upsert(group.toEntity(groupDao.find(id)))
        group
    }

    suspend fun create(name: String, category: String): ApiResult<Group> = apiCatching {
        val group = api.createGroup(name, category)
        groupDao.upsert(group.toEntity(groupDao.find(group.id)))
        group
    }

    suspend fun update(
        id: String,
        name: String?,
        whoCanPost: String?,
        whoCanAddMembers: String?,
    ): ApiResult<Group> = apiCatching {
        val group = api.updateGroup(id, name, whoCanPost, whoCanAddMembers)
        groupDao.upsert(group.toEntity(groupDao.find(id)))
        group
    }

    suspend fun delete(id: String): ApiResult<Unit> = apiCatching {
        api.deleteGroup(id)
        groupDao.delete(id)
    }

    suspend fun leave(id: String): ApiResult<Unit> = apiCatching {
        api.leaveGroup(id)
        groupDao.delete(id)
    }

    suspend fun categories(): ApiResult<List<String>> = apiCatching {
        cachedCategories ?: api.getCategories().also { cachedCategories = it }
    }

    suspend fun setMuted(id: String, muted: Boolean) {
        settingsStore.setGroupMuted(id, muted)
    }

    suspend fun isMuted(id: String): Boolean =
        id in settingsStore.settings.first().mutedGroupIds

    /** Best-effort resync of a single group after an unrecognised WebSocket `group` event. */
    suspend fun onExternalGroupChange(groupId: String?) {
        if (groupId == null) {
            refresh()
            return
        }
        when (val result = refreshGroup(groupId)) {
            is ApiResult.Failure -> {
                if (result.error.status == 404) groupDao.delete(groupId)
                else Log.w("Failed to resync group $groupId: ${result.error.type}")
            }
            is ApiResult.Success -> Unit
        }
    }
}
