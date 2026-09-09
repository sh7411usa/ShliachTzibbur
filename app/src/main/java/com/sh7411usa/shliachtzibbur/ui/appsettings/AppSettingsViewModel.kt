package com.sh7411usa.shliachtzibbur.ui.appsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.ThemeMode
import com.sh7411usa.shliachtzibbur.data.prefs.AppSettings
import com.sh7411usa.shliachtzibbur.data.prefs.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppSettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }

    fun setLanguage(tag: String) = viewModelScope.launch { settingsStore.setLanguageTag(tag) }

    fun setNotificationsEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }

    fun setSyncServiceEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setSyncServiceEnabled(enabled) }
}
