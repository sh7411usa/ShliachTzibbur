package com.sh7411usa.shliachtzibbur.core.util

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Auto-fills the SMS verification code by listening for an incoming SMS while the
 * code screen is open. Requires `RECEIVE_SMS`; without it the caller falls back
 * to manual entry. Not the SMS Retriever API — that needs Google Play Services,
 * which this app does not depend on.
 */
class SmsCodeReceiver(private val appContext: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Suspends until a code-looking SMS arrives or [timeoutMs] elapses. Cancellable. */
    suspend fun awaitCode(timeoutMs: Long): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
                    val body = runCatching {
                        Telephony.Sms.Intents.getMessagesFromIntent(intent)
                            ?.joinToString(separator = "") { it.messageBody.orEmpty() }
                            .orEmpty()
                    }.getOrDefault("")
                    val code = extractCode(body) ?: return
                    if (cont.isActive) {
                        runCatching { appContext.unregisterReceiver(this) }
                        cont.resume(code) { _, _, _ -> }
                    }
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            cont.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }
        }
    }

    companion object {
        private val DIGIT_RUN = Regex("[0-9]{4,8}")

        /** Prefer a 6-digit run (the Tzibbur code length); else the last 4–8 digit run. */
        fun extractCode(body: String): String? {
            val runs = DIGIT_RUN.findAll(body).map { it.value }.toList()
            return runs.lastOrNull { it.length == 6 } ?: runs.lastOrNull()
        }
    }
}
