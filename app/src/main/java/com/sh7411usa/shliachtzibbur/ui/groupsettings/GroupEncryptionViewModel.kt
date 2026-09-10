package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.core.crypto.KeyHex
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import com.sh7411usa.shliachtzibbur.core.util.Ids
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCrypto
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MessageRepository
import com.sh7411usa.shliachtzibbur.ui.NavArg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the per-group Encryption screen. Enforces (at the app level) that only
 * admins may turn encryption on/off or change the group (active) key; any member
 * may add or delete keys in their own local list.
 */
class GroupEncryptionViewModel(
    savedStateHandle: SavedStateHandle,
    groupRepository: GroupRepository,
    private val messageRepository: MessageRepository,
    private val cryptoStore: GroupCryptoSource,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[NavArg.GROUP_ID])

    val group: StateFlow<Group?> = groupRepository.group(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val crypto: StateFlow<GroupCrypto> = cryptoStore.crypto(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupCrypto())

    private val _keyError = MutableStateFlow(false)
    val keyError: StateFlow<Boolean> = _keyError.asStateFlow()

    fun enableEncryption() {
        viewModelScope.launch {
            if (crypto.value.keys.isEmpty()) {
                cryptoStore.addKey(groupId, newKey(), makeActive = true)
            }
            val ready = (group.value?.memberCount ?: 0) >= 3
            cryptoStore.setEnabled(groupId, enabled = true, pendingAnnounce = !ready)
            if (ready) messageRepository.sendServiceMessage(groupId, ServiceMessage.EncryptionOn)
        }
    }

    fun disableEncryption() {
        viewModelScope.launch {
            cryptoStore.setEnabled(groupId, enabled = false, pendingAnnounce = false)
            messageRepository.sendServiceMessage(groupId, ServiceMessage.EncryptionOff)
        }
    }

    fun changeGroupKey() {
        viewModelScope.launch {
            cryptoStore.addKey(groupId, newKey(), makeActive = true)
            messageRepository.sendServiceMessage(groupId, ServiceMessage.KeyChanged)
        }
    }

    /** Fire the deferred "encryption on" announcement once the group has 3 members. */
    fun announcePendingIfReady() {
        viewModelScope.launch { messageRepository.announcePendingEncryption(groupId) }
    }

    fun addKey(hex: String) {
        val clean = hex.trim()
        if (!KeyHex.isValid(clean)) {
            _keyError.update { true }
            return
        }
        _keyError.update { false }
        viewModelScope.launch {
            cryptoStore.addKey(
                groupId,
                GroupKey(Ids.newUuid(), KeyHex.normalize(clean), "Added key", System.currentTimeMillis()),
                makeActive = false,
            )
        }
    }

    fun removeKey(keyId: String) {
        viewModelScope.launch { cryptoStore.removeKey(groupId, keyId) }
    }

    /** Admin only (UI-gated): make [keyId] the key new messages are encrypted with. */
    fun useKey(keyId: String) {
        viewModelScope.launch { cryptoStore.setActiveKey(groupId, keyId) }
    }

    fun clearKeyError() = _keyError.update { false }

    private fun newKey() =
        GroupKey(Ids.newUuid(), KeyHex.generate(), "Group key", System.currentTimeMillis())
}
