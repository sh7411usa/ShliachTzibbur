package com.sh7411usa.shliachtzibbur.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sh7411usa.shliachtzibbur.core.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Persists the authenticated [Session] (bearer token + ids). Backed by
 * Preferences DataStore. The token is stored in the app's private storage; a
 * future hardening step could wrap it with the Keystore.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    val session: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN]
        val userId = prefs[Keys.USER_ID]
        val deviceId = prefs[Keys.DEVICE_ID]
        if (token.isNullOrBlank() || userId.isNullOrBlank() || deviceId.isNullOrBlank()) {
            null
        } else {
            Session(token, userId, deviceId)
        }
    }

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = session.token
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.DEVICE_ID] = session.deviceId
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
