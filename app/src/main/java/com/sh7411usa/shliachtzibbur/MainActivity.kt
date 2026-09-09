package com.sh7411usa.shliachtzibbur

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sh7411usa.shliachtzibbur.core.model.ThemeMode
import com.sh7411usa.shliachtzibbur.sync.AppForegroundState
import com.sh7411usa.shliachtzibbur.ui.navigation.ShliachNavHost
import com.sh7411usa.shliachtzibbur.ui.theme.ShliachTzibburTheme
import kotlinx.coroutines.flow.map

class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    private var pendingGroupId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingGroupId = intent?.getStringExtra(EXTRA_GROUP_ID)

        val container = (application as ShliachTzibburApp).container

        setContent {
            val themeMode by container.settingsStore.settings
                .map { it.themeMode }
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            val isSignedIn by container.sessionStore.session
                .map { it != null }
                .collectAsStateWithLifecycle(initialValue = container.initialSession != null)

            val consumedGroupId = pendingGroupId
            ShliachTzibburTheme(themeMode = themeMode) {
                ShliachNavHost(
                    isSignedIn = isSignedIn,
                    initialGroupId = consumedGroupId,
                )
            }

            if (isSignedIn) {
                LaunchNotificationPermission()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_GROUP_ID)?.let { pendingGroupId = it }
    }

    override fun onStart() {
        super.onStart()
        AppForegroundState.isInForeground = true
    }

    override fun onStop() {
        super.onStop()
        AppForegroundState.isInForeground = false
    }

    @androidx.compose.runtime.Composable
    private fun LaunchNotificationPermission() {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
    }
}
