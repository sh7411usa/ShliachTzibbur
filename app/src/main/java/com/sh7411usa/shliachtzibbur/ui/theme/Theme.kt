package com.sh7411usa.shliachtzibbur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.sh7411usa.shliachtzibbur.core.model.ThemeMode

/**
 * App theme. Honours the user's [ThemeMode] choice; [ThemeMode.SYSTEM] follows
 * the OS. Dynamic colour is intentionally not used so the brand stays consistent
 * across the wide range of supported devices.
 */
@Composable
fun ShliachTzibburTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
