package com.sh7411usa.shliachtzibbur.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.core.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Per-group encryption state stored on the device. */
@Serializable
data class GroupCrypto(
    val enabled: Boolean = false,
    /** Seq of the message that turned encryption on — plaintext before it isn't "insecure". */
    val enabledSinceSeq: Long = 0,
    /** Insertion order; newest key last. */
    val keys: List<GroupKey> = emptyList(),
    /** The key new outgoing messages are encrypted with. Null = can't send. */
    val activeKeyId: String? = null,
) {
    val activeKey: GroupKey? get() = keys.firstOrNull { it.id == activeKeyId } ?: keys.lastOrNull()

    /** Keys ordered for a decryption attempt: active first, then newest → oldest. */
    val keysForDecrypt: List<GroupKey>
        get() {
            val active = activeKey ?: return keys.asReversed()
            return listOf(active) + keys.asReversed().filter { it.id != active.id }
        }
}

/**
 * Read/write access to [GroupCrypto]. An interface so the repository, view models
 * and tests don't depend on DataStore.
 */
interface GroupCryptoSource {
    fun crypto(groupId: String): Flow<GroupCrypto>
    suspend fun setEnabled(groupId: String, enabled: Boolean, sinceSeq: Long = 0)
    suspend fun addKey(groupId: String, key: GroupKey, makeActive: Boolean)
    suspend fun removeKey(groupId: String, keyId: String)
    suspend fun setActiveKey(groupId: String, keyId: String)
}

/** No-op source used as the default so non-encryption code paths and tests need no wiring. */
object NoEncryption : GroupCryptoSource {
    override fun crypto(groupId: String): Flow<GroupCrypto> = flowOf(GroupCrypto())
    override suspend fun setEnabled(groupId: String, enabled: Boolean, sinceSeq: Long) = Unit
    override suspend fun addKey(groupId: String, key: GroupKey, makeActive: Boolean) = Unit
    override suspend fun removeKey(groupId: String, keyId: String) = Unit
    override suspend fun setActiveKey(groupId: String, keyId: String) = Unit
}

private val Context.encryptionDataStore: DataStore<Preferences> by preferencesDataStore(name = "encryption")

/**
 * Persists [GroupCrypto] per group as a JSON string in a Preferences DataStore.
 *
 * Storage is the app's private area, unencrypted at rest — the same posture as
 * the auth token in [SessionStore]. A future hardening step could wrap the blob
 * with the Android Keystore.
 */
class EncryptionStore(private val context: Context) : GroupCryptoSource {

    private val json = Json { ignoreUnknownKeys = true }

    private fun key(groupId: String) = stringPreferencesKey("group:$groupId")

    override fun crypto(groupId: String): Flow<GroupCrypto> =
        context.encryptionDataStore.data.map { prefs ->
            prefs[key(groupId)]?.let {
                runCatching { json.decodeFromString<GroupCrypto>(it) }
                    .onFailure { e -> Log.w("Corrupt GroupCrypto for $groupId: $e") }
                    .getOrDefault(GroupCrypto())
            } ?: GroupCrypto()
        }

    private suspend fun update(groupId: String, transform: (GroupCrypto) -> GroupCrypto) {
        context.encryptionDataStore.edit { prefs ->
            val current = prefs[key(groupId)]
                ?.let { runCatching { json.decodeFromString<GroupCrypto>(it) }.getOrNull() }
                ?: GroupCrypto()
            prefs[key(groupId)] = json.encodeToString(transform(current))
        }
    }

    override suspend fun setEnabled(groupId: String, enabled: Boolean, sinceSeq: Long) =
        update(groupId) {
            it.copy(
                enabled = enabled,
                enabledSinceSeq = when {
                    !enabled -> it.enabledSinceSeq
                    it.enabledSinceSeq == 0L && sinceSeq > 0 -> sinceSeq
                    else -> it.enabledSinceSeq
                },
            )
        }

    override suspend fun addKey(groupId: String, key: GroupKey, makeActive: Boolean) =
        update(groupId) {
            if (it.keys.any { existing -> existing.hex == key.hex }) {
                if (makeActive) it.copy(activeKeyId = it.keys.first { e -> e.hex == key.hex }.id) else it
            } else {
                it.copy(
                    keys = it.keys + key,
                    activeKeyId = if (makeActive || it.activeKeyId == null) key.id else it.activeKeyId,
                )
            }
        }

    override suspend fun removeKey(groupId: String, keyId: String) =
        update(groupId) {
            val remaining = it.keys.filterNot { k -> k.id == keyId }
            it.copy(
                keys = remaining,
                activeKeyId = if (it.activeKeyId == keyId) remaining.lastOrNull()?.id else it.activeKeyId,
            )
        }

    override suspend fun setActiveKey(groupId: String, keyId: String) =
        update(groupId) {
            if (it.keys.any { k -> k.id == keyId }) it.copy(activeKeyId = keyId) else it
        }
}
