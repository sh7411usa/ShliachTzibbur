package com.sh7411usa.shliachtzibbur.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.core.crypto.KeyHex
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.util.Ids
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
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
    private val crypto: GroupCryptoSource,
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

    fun create(name: String, category: String, encrypted: Boolean = false) {
        if (name.isBlank() || category.isBlank()) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = groupRepository.create(name.trim(), category)) {
                is ApiResult.Success -> {
                    val groupId = result.value.id
                    memberPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                        memberRepository.add(groupId, listOf(phone), null)
                    }
                    if (encrypted) {
                        val key = GroupKey(Ids.newUuid(), KeyHex.generate(), "Group key", System.currentTimeMillis())
                        crypto.addKey(groupId, key, makeActive = true)
                        // A new group has one member; defer the "encryption on"
                        // announcement until it reaches 3 (before that no one can post).
                        crypto.setEnabled(groupId, enabled = true, pendingAnnounce = true)
                    }
                    _state.update { it.copy(submitting = false, createdGroupId = groupId) }
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
