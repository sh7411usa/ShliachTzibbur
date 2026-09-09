package com.sh7411usa.shliachtzibbur.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MemberRepository
import com.sh7411usa.shliachtzibbur.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateGroupUiState(
    val categories: List<String> = emptyList(),
    val submitting: Boolean = false,
    val error: ApiException? = null,
    val createdGroupId: String? = null,
)

class CreateGroupViewModel(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    /** Optional contact to add to the group right after creation (from the contacts screen). */
    private val memberPhone: String? = savedStateHandle[Routes.ARG_MEMBER_PHONE]

    private val _state = MutableStateFlow(CreateGroupUiState())
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val fetched = (groupRepository.categories() as? ApiResult.Success)?.value
            _state.update {
                it.copy(categories = fetched?.takeIf { list -> list.isNotEmpty() } ?: DEFAULT_CATEGORIES)
            }
        }
    }

    fun create(name: String, category: String) {
        if (name.isBlank() || category.isBlank()) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = groupRepository.create(name.trim(), category)) {
                is ApiResult.Success -> {
                    memberPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                        memberRepository.add(result.value.id, listOf(phone), null)
                    }
                    _state.update { it.copy(submitting = false, createdGroupId = result.value.id) }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, error = result.error)
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private companion object {
        // Matches the API reference; only used if the categories endpoint is unreachable.
        val DEFAULT_CATEGORIES = listOf("family", "neighborhood", "shul", "school", "other")
    }
}
