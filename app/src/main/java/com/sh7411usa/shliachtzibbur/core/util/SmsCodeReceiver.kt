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
 * Auto-fills the SMS verification code. It both listens for an incoming SMS while
 * the code screen is open and scans messages that arrived in the last few seconds
 * (e.g. during the permission dialog). Not the SMS Retriever API — that needs
 * Google Play Services, which this app does not depend on.
 *
 * `RECEIVE_SMS` is used for the live listener, `READ_SMS` for the recent-inbox
 * scan; either alone is useful.
 */
class SmsCodeReceiver(private val appContext: Context) {

    val requiredPermissions: Array<String> =
        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    fun canReceive(): Boolean = granted(Manifest.permission.RECEIVE_SMS)
    fun canReadInbox(): Boolean = granted(Manifest.permission.READ_SMS)
    fun hasAnyPermission(): Boolean = canReceive() || canReadInbox()

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    /** Returns a code from a very recent inbox message, or suspends until one arrives (or [timeoutMs]). */
    suspend fun awaitCode(timeoutMs: Long): String? {
        recentInboxCode()?.let { return it }
        if (!canReceive()) return null
        return withTimeoutOrNull(timeoutMs) {
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
                    ContextCompat.RECEIVER_EXPORTED,
                )
                cont.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }
            }
        }
    }

    /** Newest-first scan of inbox messages from the last [WINDOW_MS]. */
    private fun recentInboxCode(): String? {
        if (!canReadInbox()) return null
        val since = System.currentTimeMillis() - WINDOW_MS
        return runCatching {
            appContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                while (cursor.moveToNext()) {
                    extractCode(cursor.getString(bodyCol).orEmpty())?.let { return@runCatching it }
                }
                null
            }
        }.getOrNull()
    }

    companion object {
        private const val WINDOW_MS = 25_000L
        private val AFTER_KEYWORD = Regex("(?i)code[^0-9]{0,6}([0-9]{4,8})")
        private val DIGIT_RUN = Regex("[0-9]{4,8}")

        /** Prefer digits right after the word "code"; then a 6-digit run; then the last 4–8 digit run. */
        fun extractCode(body: String): String? {
            AFTER_KEYWORD.find(body)?.groupValues?.get(1)?.let { return it }
            val runs = DIGIT_RUN.findAll(body).map { it.value }.toList()
            return runs.lastOrNull { it.length == 6 } ?: runs.lastOrNull()
        }
    }
}
