package com.sh7411usa.shliachtzibbur.data.repo

import com.sh7411usa.shliachtzibbur.core.model.AuthChallenge
import com.sh7411usa.shliachtzibbur.core.model.AuthMethod
import com.sh7411usa.shliachtzibbur.core.model.AuthResult
import com.sh7411usa.shliachtzibbur.core.model.Session
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.apiCatching
import com.sh7411usa.shliachtzibbur.core.util.DeviceInfo
import com.sh7411usa.shliachtzibbur.data.local.AppDatabase
import com.sh7411usa.shliachtzibbur.data.prefs.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Authentication and session lifecycle. Only [AuthMethod.Sms] is wired; the
 * method parameter exists so future flows (email, Google) slot in without
 * changing callers.
 */
class AuthRepository(
    private val api: TzibburApi,
    private val sessionStore: SessionStore,
    private val database: AppDatabase,
) {
    val session: Flow<Session?> = sessionStore.session

    suspend fun start(
        method: AuthMethod,
        phone: String,
        displayName: String?,
        region: String?,
    ): ApiResult<AuthChallenge> {
        require(method == AuthMethod.Sms) { "Only SMS auth is currently supported" }
        return apiCatching {
            api.startSms(
                phone = phone,
                displayName = displayName?.takeIf { it.isNotBlank() },
                region = region,
                platform = DeviceInfo.platform,
                deviceModel = DeviceInfo.deviceModel,
            )
        }
    }

    suspend fun verify(
        method: AuthMethod,
        challengeId: String,
        code: String,
        phone: String,
        displayName: String?,
        region: String?,
    ): ApiResult<AuthResult> {
        require(method == AuthMethod.Sms) { "Only SMS auth is currently supported" }
        return apiCatching {
            api.verifySms(
                challengeId = challengeId,
                code = code,
                phone = phone,
                displayName = displayName?.takeIf { it.isNotBlank() },
                region = region,
                platform = DeviceInfo.platform,
                deviceModel = DeviceInfo.deviceModel,
            ).also { sessionStore.save(it.session) }
        }
    }

    /**
     * Clear all local state. The cache is cleared *before* the session so the
     * screen isn't torn down (session -> null drives navigation) mid-wipe.
     * Network-side device revocation is not exposed by the API.
     */
    suspend fun signOut() {
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { database.clearAllTables() }
        }
        sessionStore.clear()
    }
}
