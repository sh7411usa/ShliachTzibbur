package com.sh7411usa.shliachtzibbur.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val refreshing: Boolean = false,
    val error: ApiException? = null,
)

class GroupsViewModel(private val groupRepository: GroupRepository) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val result = groupRepository.refresh()
            _state.update {
                it.copy(
                    refreshing = false,
                    error = (result as? ApiResult.Failure)?.error,
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
