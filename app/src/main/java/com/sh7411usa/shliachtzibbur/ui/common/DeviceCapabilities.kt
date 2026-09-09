package com.sh7411usa.shliachtzibbur.ui.common

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True on devices with a touchscreen. On non-touch (D-pad / TV / feature-phone)
 * devices the UI must remain fully operable by focus navigation, so screens use
 * this to add explicit focusable controls in place of touch-only affordances.
 */
@Composable
fun rememberIsTouchDevice(): Boolean {
    val context = LocalContext.current
    return remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}
