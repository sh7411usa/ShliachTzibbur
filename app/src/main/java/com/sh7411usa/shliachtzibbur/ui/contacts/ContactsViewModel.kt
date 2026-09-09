package com.sh7411usa.shliachtzibbur.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.repo.ContactsRepository
import com.sh7411usa.shliachtzibbur.data.repo.DeviceContact
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MemberRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val loading: Boolean = false,
    val needsPermission: Boolean = false,
    val contacts: List<DeviceContact> = emptyList(),
    val groups: List<Group> = emptyList(),
    /** contact E.164 -> group ids the contact belongs to. */
    val membership: Map<String, Set<String>> = emptyMap(),
    val error: ApiException? = null,
    val working: Boolean = false,
)

class ContactsViewModel(
    private val contactsRepository: ContactsRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsUiState(needsPermission = !contactsRepository.hasPermission()))
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    init {
        if (contactsRepository.hasPermission()) load()
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(needsPermission = !granted) }
        if (granted) load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val groups = groupRepository.groups.first()
            val membership = HashMap<String, MutableSet<String>>()
            for (group in groups) {
                memberRepository.refresh(group.id)
                memberRepository.members(group.id).first().forEach { member ->
                    member.phoneE164?.let { phone ->
                        membership.getOrPut(phone) { mutableSetOf() }.add(group.id)
                    }
                }
            }

            when (val result = contactsRepository.contacts()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        contacts = result.value,
                        groups = groups,
                        membership = membership.mapValues { entry -> entry.value.toSet() },
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, groups = groups, error = result.error)
                }
            }
        }
    }

    fun addToGroup(e164: String, groupId: String) {
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val result = memberRepository.add(groupId, listOf(e164), null)) {
                is ApiResult.Success -> {
                    memberRepository.refresh(groupId)
                    _state.update {
                        val updated = it.membership.toMutableMap()
                        updated[e164] = (updated[e164].orEmpty() + groupId)
                        it.copy(working = false, membership = updated)
                    }
                }

                is ApiResult.Failure -> _state.update { it.copy(working = false, error = result.error) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
