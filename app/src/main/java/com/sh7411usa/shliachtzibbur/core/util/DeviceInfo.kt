package com.sh7411usa.shliachtzibbur.core.util

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.sh7411usa.shliachtzibbur.core.model.AuthPlatform
import java.util.Locale

/** Device and locale facts needed for auth registration. */
object DeviceInfo {

    /** Always "android" for this client. */
    val platform: String = AuthPlatform.ANDROID.wire

    /** Human-readable model string, e.g. "Pixel 7". */
    val deviceModel: String
        get() {
            val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase(Locale.ROOT) }.orEmpty()
            val model = Build.MODEL.orEmpty()
            return when {
                model.startsWith(manufacturer, ignoreCase = true) -> model
                manufacturer.isBlank() -> model.ifBlank { "Android" }
                else -> "$manufacturer $model".trim()
            }.ifBlank { "Android" }
        }

    /**
     * Best-effort ISO 3166 region for phone-number parsing: SIM country, then
     * network country, then the current locale. Uppercased; null if unknown.
     */
    fun defaultRegion(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOfNotNull(
            tm?.simCountryIso,
            tm?.networkCountryIso,
            Locale.getDefault().country,
        )
        return candidates.firstOrNull { it.length == 2 }?.uppercase(Locale.ROOT)
    }
}
