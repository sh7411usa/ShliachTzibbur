package com.sh7411usa.shliachtzibbur.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.crypto.CryptoOutcome
import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.core.crypto.KeyHex
import com.sh7411usa.shliachtzibbur.core.crypto.MessageCrypto
import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.MessageSecurity
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.util.Ids
import com.sh7411usa.shliachtzibbur.core.util.PinControl
import com.sh7411usa.shliachtzibbur.core.util.PollSpec
import com.sh7411usa.shliachtzibbur.core.util.PollToken
import com.sh7411usa.shliachtzibbur.core.util.ReplyToken
import com.sh7411usa.shliachtzibbur.data.prefs.AppSettings
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCrypto
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import com.sh7411usa.shliachtzibbur.data.prefs.SessionStore
import com.sh7411usa.shliachtzibbur.data.prefs.SettingsStore
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.MemberRepository
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
import kotlinx.coroutines.flow.combine
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
    /** Set after a failed unlock attempt: the entered key didn't open the latest message. */
    val keyRejected: Boolean = false,
)

/** Whether the encrypted-group gate is blocking the conversation. */
enum class LockState { NotEncrypted, Unlocked, NeedsKey }

class MessagesViewModel(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val messageRepository: MessageRepository,
    profileRepository: ProfileRepository,
    private val syncManager: SyncManager,
    private val memberRepository: MemberRepository,
    sessionStore: SessionStore,
    settingsStore: SettingsStore,
    private val crypto: GroupCryptoSource,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[NavArg.GROUP_ID])

    val group: StateFlow<Group?> = groupRepository.group(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _threadQuery = MutableStateFlow("")
    val threadQuery: StateFlow<String> = _threadQuery.asStateFlow()

    val conversation: StateFlow<List<ConversationItem>> =
        combine(messageRepository.conversation(groupId), _threadQuery) { items, query ->
            if (query.isBlank()) {
                items
            } else {
                items.filter {
                    it is ConversationItem.Delivered && it.displayText.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selfUserId: StateFlow<String?> = sessionStore.session
        .map { it?.userId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groupCrypto: StateFlow<GroupCrypto> = crypto.crypto(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupCrypto())

    /** Admin user ids in this group — for gating "Pin" and honouring pin control messages. */
    val adminIds: StateFlow<Set<String>> = memberRepository.members(groupId)
        .map { members -> members.filter { it.role == Role.ADMIN }.map { it.userId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The unfiltered stream (thread search doesn't affect the lock decision). */
    private val rawConversation: StateFlow<List<ConversationItem>> =
        messageRepository.conversation(groupId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lockState: StateFlow<LockState> =
        combine(rawConversation, groupCrypto) { items, gc ->
            when {
                !gc.enabled -> LockState.NotEncrypted
                newestCipher(items)?.security == MessageSecurity.Undecryptable -> LockState.NeedsKey
                else -> LockState.Unlocked
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockState.NotEncrypted)

    /** The server's byte limit for a message body. The composer shows the *projected* length against it. */
    val maxMessageChars: StateFlow<Int> = group
        .map { it?.limits?.messageMaxLength ?: MessageRepository.MAX_BODY_LENGTH }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageRepository.MAX_BODY_LENGTH)

    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    init {
        AppForegroundState.visibleGroupId = groupId
        refreshLatest()
        loadOlder()
        viewModelScope.launch { messageRepository.sweepStuckOutbox(groupId) }
        viewModelScope.launch { memberRepository.refresh(groupId) }
        viewModelScope.launch { messageRepository.announcePendingEncryption(groupId) }
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
        // Everything in the thread while this screen is open counts as read.
        viewModelScope.launch {
            messageRepository.conversation(groupId).collect { items ->
                items.filterIsInstance<ConversationItem.Delivered>()
                    .maxOfOrNull { it.message.seq }
                    ?.let { messageRepository.markRead(groupId, it) }
            }
        }
    }

    fun setThreadQuery(query: String) = _threadQuery.update { query }

    private fun newestCipher(items: List<ConversationItem>): ConversationItem.Delivered? =
        items.filterIsInstance<ConversationItem.Delivered>()
            .filter { MessageCrypto.isCipherText(it.message.text) }
            .maxByOrNull { it.message.seq }

    /**
     * Add [hex] as a key for this group. If it opens the newest encrypted message
     * it also becomes the active (send) key and clears the lock; otherwise it is
     * kept (it may unlock older messages) and [MessagesUiState.keyRejected] is set.
     */
    fun submitKey(hex: String) {
        val clean = hex.trim()
        if (!KeyHex.isValid(clean)) {
            _state.update { it.copy(keyRejected = true) }
            return
        }
        viewModelScope.launch {
            val key = GroupKey(
                id = Ids.newUuid(),
                hex = KeyHex.normalize(clean),
                label = "Key " + KeyHex.normalize(clean).take(8),
                addedAtMillis = System.currentTimeMillis(),
            )
            val newest = newestCipher(rawConversation.value)?.message
            val opensNewest = newest == null ||
                MessageCrypto.decrypt(newest.text, newest.seq, newest.senderId, listOf(key)) is CryptoOutcome.Decrypted
            crypto.addKey(groupId, key, makeActive = opensNewest)
            _state.update { it.copy(keyRejected = !opensNewest) }
        }
    }

    fun clearKeyRejected() = _state.update { it.copy(keyRejected = false) }

    fun refreshLatest() {
        viewModelScope.launch {
            when (val result = messageRepository.refreshLatest(groupId)) {
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
                is ApiResult.Success -> Unit
            }
            groupRepository.refreshGroup(groupId)
            messageRepository.announcePendingEncryption(groupId)
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

    /** React to message [targetSeq] with [emoji]; delivered as an `RE:<seq> <emoji>` reply. */
    fun react(targetSeq: Long, emoji: String) = send(ReplyToken.format(targetSeq, emoji))

    fun createPoll(question: String, options: List<String>) {
        val opts = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (question.isBlank() || opts.size < 2) return
        send(PollSpec.format(question.trim(), opts))
    }

    /** Cast (or attempt to cast — only the first counts) a vote in poll [pollSeq]. */
    fun vote(pollSeq: Long, choice: Int) = send(PollToken.formatVote(pollSeq, choice))

    fun endPoll(pollSeq: Long) = send(PollToken.formatEnd(pollSeq))

    fun pin(seq: Long) {
        viewModelScope.launch { messageRepository.sendPinControl(groupId, PinControl.Pin(seq)) }
    }

    fun unpin(seq: Long) {
        viewModelScope.launch { messageRepository.sendPinControl(groupId, PinControl.Unpin(seq)) }
    }

    fun retry(clientMessageId: String) {
        viewModelScope.launch { messageRepository.retry(clientMessageId) }
    }

    fun deleteFailed(clientMessageId: String) {
        viewModelScope.launch { messageRepository.deleteOutbox(clientMessageId) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        if (AppForegroundState.visibleGroupId == groupId) {
            AppForegroundState.visibleGroupId = null
        }
    }
}
