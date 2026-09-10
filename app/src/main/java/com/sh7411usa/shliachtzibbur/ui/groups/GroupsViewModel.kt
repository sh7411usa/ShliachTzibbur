package com.sh7411usa.shliachtzibbur.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MessageRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val refreshing: Boolean = false,
    val error: ApiException? = null,
)

/** A message that matched a search, with its group's display name and the query that hit it. */
data class MessageHit(val groupId: String, val groupName: String, val message: Message, val query: String)

data class SearchResults(
    val groups: List<Group> = emptyList(),
    val messages: List<MessageHit> = emptyList(),
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GroupsViewModel(
    private val groupRepository: GroupRepository,
    private val messageRepository: MessageRepository,
    cryptoStore: GroupCryptoSource,
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Group ids that are encrypted, for the lock badge. */
    val encryptedIds: StateFlow<Set<String>> = cryptoStore.enabledGroupIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<SearchResults> = _query
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(SearchResults())
            } else {
                groupRepository.groups.map { groups ->
                    val nameHits = groups.filter { it.name.contains(q, ignoreCase = true) }
                    val byId = groups.associateBy { it.id }
                    val msgHits = messageRepository.search(q).mapNotNull { msg ->
                        val group = byId[msg.groupId] ?: return@mapNotNull null
                        MessageHit(group.id, group.name, msg, q)
                    }
                    SearchResults(nameHits, msgHits)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun setQuery(q: String) = _query.update { q }

    fun leave(groupId: String) {
        viewModelScope.launch { groupRepository.leave(groupId) }
    }

    fun delete(groupId: String) {
        viewModelScope.launch { groupRepository.delete(groupId) }
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
