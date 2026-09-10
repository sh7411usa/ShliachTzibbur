package com.sh7411usa.shliachtzibbur.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * A best-effort "current location" without Google Play Services: the most recent
 * fix any provider already has. Returns a shareable `geo:` URI or null.
 */
object LastLocation {

    val permissions: Array<String> = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun hasPermission(context: Context): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** e.g. `geo:37.42,-122.08?q=37.42,-122.08(Shared location)` */
    fun geoUri(context: Context): String? {
        if (!hasPermission(context)) return null
        val lm = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
        val best = try {
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
                .filter { lm.allProviders.contains(it) }
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        } ?: return null

        val lat = String.format(Locale.US, "%.6f", best.latitude)
        val lng = String.format(Locale.US, "%.6f", best.longitude)
        return "geo:$lat,$lng?q=$lat,$lng"
    }
}
