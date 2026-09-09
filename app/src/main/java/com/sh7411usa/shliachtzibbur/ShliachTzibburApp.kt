package com.sh7411usa.shliachtzibbur

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sh7411usa.shliachtzibbur.core.model.ThemeMode
import com.sh7411usa.shliachtzibbur.di.AppContainer
import com.sh7411usa.shliachtzibbur.ui.locale.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ShliachTzibburApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        applyPersistedPreferencesOnStartup()
        observePreferenceChanges()
        observeBackgroundSyncState()

        container.notificationHelper.ensureChannels()
    }

    private fun applyPersistedPreferencesOnStartup() {
        val settings = runBlocking { container.settingsStore.settings.first() }
        AppCompatDelegate.setDefaultNightMode(settings.themeMode.toNightMode())
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(settings.languageTag),
        )
    }

    private fun observePreferenceChanges() {
        container.settingsStore.settings
            .map { it.themeMode }
            .distinctUntilChanged()
            .onEach { AppCompatDelegate.setDefaultNightMode(it.toNightMode()) }
            .launchIn(appScope)

        container.settingsStore.settings
            .map { it.languageTag }
            .distinctUntilChanged()
            .onEach { LocaleManager.apply(it) }
            .launchIn(appScope)
    }

    private fun observeBackgroundSyncState() {
        combine(
            container.sessionStore.session.map { it != null }.distinctUntilChanged(),
            container.settingsStore.settings.map { it.syncServiceEnabled }.distinctUntilChanged(),
        ) { signedIn, serviceEnabled -> signedIn to serviceEnabled }
            .onEach { (signedIn, serviceEnabled) ->
                container.syncController.apply(signedIn, serviceEnabled)
                if (signedIn) container.syncController.requestImmediatePoll()
            }
            .launchIn(appScope)
    }

    private fun ThemeMode.toNightMode(): Int = when (this) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
}
