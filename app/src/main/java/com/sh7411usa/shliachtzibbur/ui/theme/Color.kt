package com.sh7411usa.shliachtzibbur.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Restrained two-tone palette: a deep indigo primary with a warm amber accent.
 * No gradients, no decorative colour. Both schemes are defined in full so the
 * app looks deliberate on every device rather than relying on Material defaults.
 */

private val Indigo10 = Color(0xFF11103A)
private val Indigo20 = Color(0xFF23225C)
private val Indigo40 = Color(0xFF3F3D9C)
private val Indigo80 = Color(0xFFBEBCFF)
private val Indigo90 = Color(0xFFE2E0FF)

private val Amber30 = Color(0xFF6E4E00)
private val Amber40 = Color(0xFF8A6300)
private val Amber80 = Color(0xFFF5BE48)
private val Amber90 = Color(0xFFFFDEA6)

private val Neutral10 = Color(0xFF1A1B1F)
private val Neutral20 = Color(0xFF2F3033)
private val Neutral90 = Color(0xFFE3E2E6)
private val Neutral95 = Color(0xFFF2F0F4)
private val Neutral99 = Color(0xFFFDFBFF)

private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)

val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber30,
    tertiary = Indigo40,
    onTertiary = Color.White,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral20,
    outline = Color(0xFF767680),
    error = Red40,
    onError = Color.White,
)

val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo40,
    onPrimaryContainer = Indigo90,
    secondary = Amber80,
    onSecondary = Amber30,
    secondaryContainer = Amber40,
    onSecondaryContainer = Amber90,
    tertiary = Indigo80,
    onTertiary = Indigo20,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    outline = Color(0xFF90909A),
    error = Red80,
    onError = Color(0xFF690005),
)
