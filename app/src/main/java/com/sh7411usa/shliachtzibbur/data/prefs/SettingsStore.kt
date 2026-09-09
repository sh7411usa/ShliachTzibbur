package com.sh7411usa.shliachtzibbur.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sh7411usa.shliachtzibbur.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** All persisted user/app preferences. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** BCP-47 tag, or empty string to follow the system language. */
    val languageTag: String = "",
    val notificationsEnabled: Boolean = true,
    /** Whether the persistent WebSocket sync service may run. */
    val syncServiceEnabled: Boolean = false,
    val mutedGroupIds: Set<String> = emptySet(),
    /** Last phone number used to sign in, prefilled on the login screen. Survives sign-out. */
    val lastPhoneE164: String = "",
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val SYNC_SERVICE = booleanPreferencesKey("sync_service_enabled")
        val MUTED = stringSetPreferencesKey("muted_group_ids")
        val LAST_PHONE = stringPreferencesKey("last_phone_e164")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromName(prefs[Keys.THEME]),
            languageTag = prefs[Keys.LANGUAGE].orEmpty(),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            syncServiceEnabled = prefs[Keys.SYNC_SERVICE] ?: false,
            mutedGroupIds = prefs[Keys.MUTED].orEmpty(),
            lastPhoneE164 = prefs[Keys.LAST_PHONE].orEmpty(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setLanguageTag(tag: String) =
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = tag }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setSyncServiceEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.SYNC_SERVICE] = enabled }

    suspend fun setLastPhoneE164(phone: String) =
        context.settingsDataStore.edit { it[Keys.LAST_PHONE] = phone }

    suspend fun setGroupMuted(groupId: String, muted: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.MUTED].orEmpty().toMutableSet()
            if (muted) current.add(groupId) else current.remove(groupId)
            prefs[Keys.MUTED] = current
        }
    }
}
