package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.AddMembersResult
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.util.PhoneNumbers
import com.sh7411usa.shliachtzibbur.data.repo.ContactsRepository
import com.sh7411usa.shliachtzibbur.data.repo.DeviceContact
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
    /** E.164 numbers the user has selected (contacts + typed). */
    val selected: Set<String> = emptySet(),
    /** Typed numbers not backed by a contact, shown as their own rows. */
    val manualNumbers: List<String> = emptyList(),
    val working: Boolean = false,
    val result: AddMembersResult? = null,
    val error: ApiException? = null,
) {
    val filteredContacts: List<DeviceContact>
        get() = if (query.isBlank()) contacts else contacts.filter {
            it.name.contains(query, ignoreCase = true) || it.e164.contains(query)
        }
}

class AddMembersViewModel(
    savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[NavArg.GROUP_ID])

    private val _state = MutableStateFlow(
        AddMembersUiState(needsContactsPermission = !contactsRepository.hasPermission()),
    )
    val state: StateFlow<AddMembersUiState> = _state.asStateFlow()

    init {
        load()
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
            _state.update {
                it.copy(loading = false, contacts = contacts, existingMembers = existing)
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun toggle(e164: String) = _state.update {
        if (e164 in it.existingMembers) return@update it
        val next = it.selected.toMutableSet()
        if (!next.add(e164)) next.remove(e164)
        it.copy(selected = next)
    }

    fun addManualNumber(raw: String) {
        val e164 = PhoneNumbers.toE164("", if (raw.trim().startsWith("+")) raw.trim() else "+${raw.trim()}")
        if (!PhoneNumbers.looksValid(e164)) {
            _state.update { it.copy(error = ApiException("auth_error_invalid_phone", 0, null)) }
            return
        }
        _state.update {
            it.copy(
                manualNumbers = (it.manualNumbers + e164).distinct(),
                selected = it.selected + e164,
                error = null,
            )
        }
    }

    fun submit() {
        val phones = _state.value.selected.toList()
        if (phones.isEmpty()) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val result = memberRepository.add(groupId, phones, null)) {
                is ApiResult.Success -> _state.update { it.copy(working = false, result = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(working = false, error = result.error) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
