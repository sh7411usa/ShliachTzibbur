package com.sh7411usa.shliachtzibbur.fakes

import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCrypto
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [GroupCryptoSource] for repository / view-model tests. */
class FakeGroupCryptoSource(initial: GroupCrypto = GroupCrypto()) : GroupCryptoSource {

    val state = MutableStateFlow(initial)

    override fun crypto(groupId: String): Flow<GroupCrypto> = state.map { it }

    override suspend fun setEnabled(groupId: String, enabled: Boolean, sinceSeq: Long) {
        state.value = state.value.copy(
            enabled = enabled,
            enabledSinceSeq = if (enabled && state.value.enabledSinceSeq == 0L && sinceSeq > 0) {
                sinceSeq
            } else {
                state.value.enabledSinceSeq
            },
        )
    }

    override suspend fun addKey(groupId: String, key: GroupKey, makeActive: Boolean) {
        val cur = state.value
        if (cur.keys.any { it.hex == key.hex }) return
        state.value = cur.copy(
            keys = cur.keys + key,
            activeKeyId = if (makeActive || cur.activeKeyId == null) key.id else cur.activeKeyId,
        )
    }

    override suspend fun removeKey(groupId: String, keyId: String) {
        val remaining = state.value.keys.filterNot { it.id == keyId }
        state.value = state.value.copy(
            keys = remaining,
            activeKeyId = if (state.value.activeKeyId == keyId) remaining.lastOrNull()?.id else state.value.activeKeyId,
        )
    }

    override suspend fun setActiveKey(groupId: String, keyId: String) {
        if (state.value.keys.any { it.id == keyId }) {
            state.value = state.value.copy(activeKeyId = keyId)
        }
    }
}
