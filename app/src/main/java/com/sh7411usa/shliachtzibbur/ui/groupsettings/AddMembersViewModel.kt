package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.AddMembersResult
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import com.sh7411usa.shliachtzibbur.data.repo.ContactsRepository
import com.sh7411usa.shliachtzibbur.data.repo.DeviceContact
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MemberRepository
import com.sh7411usa.shliachtzibbur.ui.NavArg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddMembersUiState(
    val loading: Boolean = false,
    val needsContactsPermission: Boolean = false,
    val contacts: List<DeviceContact> = emptyList(),
    /** E.164 numbers already in the group. */
    val existingMembers: Set<String> = emptySet(),
    val query: String = "",
    /** Selected contacts, by E.164. */
    val selected: Set<String> = emptySet(),
    /** Numbers the user typed in (shown as removable chips). The server parses
     *  them with [region] when they aren't already E.164. */
    val typed: List<String> = emptyList(),
    val working: Boolean = false,
    val result: AddMembersResult? = null,
    val error: ApiException? = null,
    /** Encryption context for the key-share offer. */
    val encrypted: Boolean = false,
    val groupName: String = "",
    val keyHex: String? = null,
    val shareKey: Boolean = true,
    /** After a successful add: registered new members who can be SMS'd the key. */
    val keyShareTargets: List<KeyShareTarget> = emptyList(),
) {
    val totalToAdd: Int get() = selected.size + typed.size

    val filteredContacts: List<DeviceContact>
        get() = if (query.isBlank()) contacts else contacts.filter {
            it.name.contains(query, ignoreCase = true) || it.e164.contains(query)
        }
}

/** A newly-added member who can be sent the encryption key by SMS. */
data class KeyShareTarget(val name: String, val e164: String)

class AddMembersViewModel(
    savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val cryptoStore: GroupCryptoSource,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[NavArg.GROUP_ID])

    private var region: String? = null

    private val _state = MutableStateFlow(
        AddMembersUiState(needsContactsPermission = !contactsRepository.hasPermission()),
    )
    val state: StateFlow<AddMembersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setRegion(r: String?) {
        region = r?.takeIf { it.isNotBlank() }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(needsContactsPermission = !granted) }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val existing = memberRepository.members(groupId).first()
                .mapNotNull { it.phoneE164 }
                .toSet()
            val contacts = if (contactsRepository.hasPermission()) {
                (contactsRepository.contacts() as? ApiResult.Success)?.value.orEmpty()
            } else {
                emptyList()
            }
            val gc = cryptoStore.crypto(groupId).first()
            val name = groupRepository.group(groupId).first()?.name.orEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    contacts = contacts,
                    existingMembers = existing,
                    encrypted = gc.enabled && gc.activeKey != null,
                    keyHex = gc.activeKey?.hex,
                    groupName = name,
                )
            }
        }
    }

    fun setShareKey(share: Boolean) = _state.update { it.copy(shareKey = share) }

    fun clearKeyShareTargets() = _state.update { it.copy(keyShareTargets = emptyList()) }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun toggle(e164: String) = _state.update {
        if (e164 in it.existingMembers) return@update it
        val next = it.selected.toMutableSet()
        if (!next.add(e164)) next.remove(e164)
        it.copy(selected = next)
    }

    fun addTypedNumber(raw: String) {
        val cleaned = raw.filter { it.isDigit() || it == '+' }.let { s ->
            if (s.startsWith("+")) "+" + s.drop(1).filter(Char::isDigit) else s.filter(Char::isDigit)
        }
        if (cleaned.count(Char::isDigit) < 4) {
            _state.update { it.copy(error = ApiException("auth_error_invalid_phone", 0, null)) }
            return
        }
        _state.update { it.copy(typed = (it.typed + cleaned).distinct(), error = null) }
    }

    fun removeTypedNumber(number: String) =
        _state.update { it.copy(typed = it.typed - number) }

    fun submit() {
        val phones = _state.value.selected.toList() + _state.value.typed
        if (phones.isEmpty()) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val result = memberRepository.add(groupId, phones, region)) {
                is ApiResult.Success -> _state.update { s ->
                    val targets = if (s.encrypted && s.shareKey && s.keyHex != null) {
                        result.value.added
                            .mapNotNull { m -> m.phoneE164?.let { KeyShareTarget(m.displayName, it) } }
                    } else {
                        emptyList()
                    }
                    s.copy(working = false, result = result.value, keyShareTargets = targets)
                }

                is ApiResult.Failure -> _state.update { it.copy(working = false, error = result.error) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
