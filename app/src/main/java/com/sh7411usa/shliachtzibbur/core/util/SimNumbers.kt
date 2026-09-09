package com.sh7411usa.shliachtzibbur.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** A phone number offered by a SIM, for the "which number?" chooser. */
data class SimOption(val label: String, val e164: String)

/** Result of trying to discover the device's own phone number(s). */
sealed interface PhoneDetection {
    /** A single usable number was found. */
    data class Prefill(val e164: String) : PhoneDetection

    /** Multiple SIMs with numbers; ask the user which. */
    data class ChooseSim(val options: List<SimOption>) : PhoneDetection

    /** We need a permission before we can look. */
    data object NeedsPermission : PhoneDetection

    /** Nothing usable (carrier doesn't expose the number). */
    data object None : PhoneDetection
}

/**
 * Best-effort discovery of the device's own MSISDN. Carriers frequently don't
 * populate this, so every path degrades to manual entry.
 */
object SimNumbers {

    /** The runtime permissions that let us read SIM numbers, by SDK level. */
    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
        } else {
            arrayOf(Manifest.permission.READ_PHONE_STATE)
        }

    fun hasPermission(context: Context): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun detect(context: Context): PhoneDetection {
        if (!hasPermission(context)) return PhoneDetection.NeedsPermission

        val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
            ?: return PhoneDetection.None

        val infos = try {
            sm.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            return PhoneDetection.NeedsPermission
        }

        val options = infos.mapNotNull { info ->
            val raw = info.number?.trim().orEmpty()
            if (raw.isBlank()) return@mapNotNull null
            val e164 = if (raw.startsWith("+")) raw else PhoneNumbers.toE164("", raw)
            if (!PhoneNumbers.looksValid(e164)) return@mapNotNull null
            val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${info.simSlotIndex + 1}"
            SimOption(label = carrier, e164 = e164)
        }.distinctBy { it.e164 }

        return when (options.size) {
            0 -> PhoneDetection.None
            1 -> PhoneDetection.Prefill(options.first().e164)
            else -> PhoneDetection.ChooseSim(options)
        }
    }
}
