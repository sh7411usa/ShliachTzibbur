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
    /** Whether the persistent WebSocket sync service may run. On by default. */
    val syncServiceEnabled: Boolean = true,
    val mutedGroupIds: Set<String> = emptySet(),
    /** Last phone number used to sign in, prefilled on the login screen. Survives sign-out. */
    val lastPhoneE164: String = "",
    /** Render Markdown in message bodies. */
    val messagesMarkdown: Boolean = true,
    /** Show a small "#<seq>" above each message. */
    val showMessageSeq: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val SYNC_SERVICE = booleanPreferencesKey("sync_service_enabled")
        val MUTED = stringSetPreferencesKey("muted_group_ids")
        val LAST_PHONE = stringPreferencesKey("last_phone_e164")
        val MESSAGES_MARKDOWN = booleanPreferencesKey("messages_markdown")
        val SHOW_MESSAGE_SEQ = booleanPreferencesKey("show_message_seq")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromName(prefs[Keys.THEME]),
            languageTag = prefs[Keys.LANGUAGE].orEmpty(),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            syncServiceEnabled = prefs[Keys.SYNC_SERVICE] ?: true,
            mutedGroupIds = prefs[Keys.MUTED].orEmpty(),
            lastPhoneE164 = prefs[Keys.LAST_PHONE].orEmpty(),
            messagesMarkdown = prefs[Keys.MESSAGES_MARKDOWN] ?: true,
            showMessageSeq = prefs[Keys.SHOW_MESSAGE_SEQ] ?: false,
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

    suspend fun setMessagesMarkdown(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.MESSAGES_MARKDOWN] = enabled }

    suspend fun setShowMessageSeq(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.SHOW_MESSAGE_SEQ] = enabled }

    suspend fun setGroupMuted(groupId: String, muted: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.MUTED].orEmpty().toMutableSet()
            if (muted) current.add(groupId) else current.remove(groupId)
            prefs[Keys.MUTED] = current
        }
    }
}
