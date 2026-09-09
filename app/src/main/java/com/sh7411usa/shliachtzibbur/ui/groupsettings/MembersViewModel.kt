package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.AddMembersResult
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.Member
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.util.PhoneNumbers
import com.sh7411usa.shliachtzibbur.data.prefs.SessionStore
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MemberRepository
import com.sh7411usa.shliachtzibbur.ui.NavArg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MembersUiState(
    val working: Boolean = false,
    val error: ApiException? = null,
    val addResult: AddMembersResult? = null,
)

class MembersViewModel(
    savedStateHandle: SavedStateHandle,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    sessionStore: SessionStore,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[NavArg.GROUP_ID])

    val members: StateFlow<List<Member>> = memberRepository.members(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val group: StateFlow<Group?> = groupRepository.group(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selfUserId: StateFlow<String?> = sessionStore.session
        .map { it?.userId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(MembersUiState())
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            (memberRepository.refresh(groupId) as? ApiResult.Failure)?.let { failure ->
                _state.update { it.copy(error = failure.error) }
            }
        }
    }

    /** [raw] is free text: one phone number per line. */
    fun addMembers(raw: String, region: String?) {
        val phones = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith("+")) it else PhoneNumbers.toE164("", it) }
            .distinct()
        if (phones.isEmpty()) return

        _state.update { it.copy(working = true, error = null, addResult = null) }
        viewModelScope.launch {
            when (val result = memberRepository.add(groupId, phones, region?.takeIf { it.isNotBlank() })) {
                is ApiResult.Success -> _state.update {
                    it.copy(working = false, addResult = result.value)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(working = false, error = result.error)
                }
            }
            groupRepository.refreshGroup(groupId)
        }
    }

    fun setRole(userId: String, role: Role) {
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    working = false,
                    error = (memberRepository.changeRole(groupId, userId, role) as? ApiResult.Failure)?.error,
                )
            }
        }
    }

    fun remove(userId: String) {
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    working = false,
                    error = (memberRepository.remove(groupId, userId) as? ApiResult.Failure)?.error,
                )
            }
            groupRepository.refreshGroup(groupId)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearAddResult() = _state.update { it.copy(addResult = null) }
}
