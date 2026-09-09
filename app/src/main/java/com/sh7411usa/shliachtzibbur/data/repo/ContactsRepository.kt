package com.sh7411usa.shliachtzibbur.data.repo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.apiCatching
import com.sh7411usa.shliachtzibbur.core.util.Log
import com.sh7411usa.shliachtzibbur.core.util.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** A phone-book entry, matched (or not) to a Tzibbur account. */
data class DeviceContact(
    val name: String,
    val e164: String,
    val isRegistered: Boolean,
)

/**
 * Reads device contacts and checks which are on Tzibbur. Relies on Android's
 * pre-computed `NORMALIZED_NUMBER` (E.164) so we don't need libphonenumber.
 */
class ContactsRepository(
    private val appContext: Context,
    private val api: TzibburApi,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** All contacts with a usable E.164 number, one row per distinct number, with registration status. */
    suspend fun contacts(): ApiResult<List<DeviceContact>> = apiCatching {
        val raw = withContext(Dispatchers.IO) { readDeviceNumbers() }
        if (raw.isEmpty()) return@apiCatching emptyList()

        val registered = mutableSetOf<String>()
        raw.keys.chunked(100).forEach { chunk ->
            registered += api.checkContacts(chunk, region = null)
        }

        raw.entries
            .map { (e164, name) -> DeviceContact(name = name, e164 = e164, isRegistered = e164 in registered) }
            .sortedWith(compareByDescending<DeviceContact> { it.isRegistered }.thenBy { it.name.lowercase(Locale.getDefault()) })
    }

    private fun readDeviceNumbers(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
        )
        appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val normCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)?.trim().orEmpty().ifBlank { continue }
                val normalized = cursor.getString(normCol)?.trim().orEmpty()
                val e164 = when {
                    normalized.startsWith("+") && PhoneNumbers.looksValid(normalized) -> normalized
                    else -> {
                        val fallback = cursor.getString(numberCol)?.trim().orEmpty()
                        if (fallback.startsWith("+") && PhoneNumbers.looksValid(fallback)) fallback else continue
                    }
                }
                result.putIfAbsent(e164, name)
            }
        } ?: Log.w("Contacts query returned null cursor")
        return result
    }
}
