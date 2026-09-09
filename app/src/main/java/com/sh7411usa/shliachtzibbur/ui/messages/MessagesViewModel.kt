package com.sh7411usa.shliachtzibbur.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.prefs.SessionStore
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MessageRepository
import com.sh7411usa.shliachtzibbur.data.repo.ProfileRepository
import com.sh7411usa.shliachtzibbur.sync.AppForegroundState
import com.sh7411usa.shliachtzibbur.sync.SyncManager
import com.sh7411usa.shliachtzibbur.ui.NavArg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MessagesUiState(
    val loadingHistory: Boolean = false,
    val hasMoreHistory: Boolean = true,
    val sending: Boolean = false,
    val error: ApiException? = null,
)

class MessagesViewModel(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val messageRepository: MessageRepository,
    profileRepository: ProfileRepository,
    private val syncManager: SyncManager,
    sessionStore: SessionStore,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[NavArg.GROUP_ID])

    val group: StateFlow<Group?> = groupRepository.group(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conversation: StateFlow<List<ConversationItem>> = messageRepository.conversation(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selfUserId: StateFlow<String?> = sessionStore.session
        .map { it?.userId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    init {
        AppForegroundState.visibleGroupId = groupId
        refreshLatest()
        loadOlder()
        viewModelScope.launch { messageRepository.sweepStuckOutbox(groupId) }
        // Live updates while this screen is open (in addition to any sync service).
        viewModelScope.launch {
            runCatching { syncManager.runWebSocketSession() }
        }
        // Poll for new messages while the screen is open (fallback if the live
        // WebSocket push isn't flowing), and confirm/expire queued sends.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                messageRepository.refreshLatest(groupId)
                if (conversation.value.any { it is ConversationItem.Pending }) {
                    messageRepository.sweepStuckOutbox(groupId)
                }
            }
        }
    }

    fun refreshLatest() {
        viewModelScope.launch {
            when (val result = messageRepository.refreshLatest(groupId)) {
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
                is ApiResult.Success -> Unit
            }
            groupRepository.refreshGroup(groupId)
        }
    }

    fun loadOlder() {
        if (_state.value.loadingHistory || !_state.value.hasMoreHistory) return
        _state.update { it.copy(loadingHistory = true) }
        viewModelScope.launch {
            when (val result = messageRepository.loadOlder(groupId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loadingHistory = false, hasMoreHistory = result.value)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loadingHistory = false, error = result.error)
                }
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val result = messageRepository.send(groupId, text)
            _state.update {
                it.copy(sending = false, error = (result as? ApiResult.Failure)?.error)
            }
            messageRepository.refreshLatest(groupId)
        }
    }

    fun retry(clientMessageId: String) {
        viewModelScope.launch { messageRepository.retry(clientMessageId) }
    }

    fun deleteFailed(clientMessageId: String) {
        viewModelScope.launch { messageRepository.deleteOutbox(clientMessageId) }
    }

    fun markReadUpTo(seq: Long) {
        viewModelScope.launch { messageRepository.markRead(groupId, seq) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        if (AppForegroundState.visibleGroupId == groupId) {
            AppForegroundState.visibleGroupId = null
        }
    }
}
