package com.sh7411usa.shliachtzibbur.data.repo

import com.sh7411usa.shliachtzibbur.core.model.Device
import com.sh7411usa.shliachtzibbur.core.model.User
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.apiCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in user's profile and devices. The user object is cached in memory and refreshed on demand. */
class ProfileRepository(private val api: TzibburApi) {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    suspend fun refresh(): ApiResult<User> =
        apiCatching { api.getMe().also { _user.value = it } }

    suspend fun updateDisplayName(displayName: String): ApiResult<User> =
        apiCatching { api.updateDisplayName(displayName).also { _user.value = it } }

    suspend fun devices(): ApiResult<List<Device>> =
        apiCatching { api.getDevices() }

    fun clear() {
        _user.value = null
    }
}
