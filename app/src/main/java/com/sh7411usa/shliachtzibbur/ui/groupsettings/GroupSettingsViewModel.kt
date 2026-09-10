package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.WhoCanAddMembers
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.ui.NavArg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupSettingsUiState(
    val saving: Boolean = false,
    val error: ApiException? = null,
    val left: Boolean = false,
    val deleted: Boolean = false,
)

class GroupSettingsViewModel(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    cryptoStore: GroupCryptoSource,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[NavArg.GROUP_ID])

    val group: StateFlow<Group?> = groupRepository.group(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val encryptionEnabled: StateFlow<Boolean> = cryptoStore.crypto(groupId)
        .map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _state = MutableStateFlow(GroupSettingsUiState())
    val state: StateFlow<GroupSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { groupRepository.refreshGroup(groupId) }
    }

    fun rename(name: String) = patch(name = name.trim().takeIf { it.isNotBlank() })

    fun setWhoCanPost(value: WhoCanPost) = patch(whoCanPost = value.wire)

    fun setWhoCanAddMembers(value: WhoCanAddMembers) = patch(whoCanAddMembers = value.wire)

    private fun patch(name: String? = null, whoCanPost: String? = null, whoCanAddMembers: String? = null) {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = groupRepository.update(groupId, name, whoCanPost, whoCanAddMembers)
            _state.update {
                it.copy(saving = false, error = (result as? ApiResult.Failure)?.error)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        viewModelScope.launch { groupRepository.setMuted(groupId, muted) }
    }

    fun leave() {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = groupRepository.leave(groupId)) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, left = true) }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, error = result.error) }
            }
        }
    }

    fun delete() {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = groupRepository.delete(groupId)) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, error = result.error) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
