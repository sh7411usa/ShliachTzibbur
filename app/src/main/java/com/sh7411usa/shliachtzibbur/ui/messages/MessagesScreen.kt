package com.sh7411usa.shliachtzibbur.ui.messages

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.crypto.KeyHex
import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.GroupKind
import com.sh7411usa.shliachtzibbur.core.model.MessageSecurity
import com.sh7411usa.shliachtzibbur.core.model.OutboxState
import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost
import com.sh7411usa.shliachtzibbur.core.util.Reactions
import com.sh7411usa.shliachtzibbur.core.util.ReplyToken
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.ConfirmDialog
import com.sh7411usa.shliachtzibbur.ui.common.MessageText
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight
import com.sh7411usa.shliachtzibbur.ui.common.rememberIsTouchDevice
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    onOpenSettings: (String) -> Unit,
    viewModel: MessagesViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val items by viewModel.conversation.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selfId by viewModel.selfUserId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val threadQuery by viewModel.threadQuery.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val groupCrypto by viewModel.groupCrypto.collectAsStateWithLifecycle()
    val maxMessageChars by viewModel.maxMessageChars.collectAsStateWithLifecycle()
    val adminIds by viewModel.adminIds.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var replyToSeq by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPasteKey by rememberSaveable { mutableStateOf(false) }
    var showPollComposer by rememberSaveable { mutableStateOf(false) }

    val locked = lockState == LockState.NeedsKey
    val selfIsAdmin = selfId != null && selfId in adminIds
    val isTouch = rememberIsTouchDevice()

    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Reactions hang on their target; polls / votes / pins / service messages
    // become tags, cards or summary rows.
    val now = remember(items) { System.currentTimeMillis() }
    val derived = remember(items, selfId, adminIds, now) {
        deriveConversation(items, selfId, adminIds, now)
    }
    val rows = derived.rows
    val reactionsBySeq = derived.reactionsBySeq

    // Reply-quote lookup, over the decrypted display text.
    val bySeq = remember(rows) {
        rows.filterIsInstance<ConvRow.Msg>().associateBy { it.item.message.seq }
    }
    fun quotedFor(seq: Long): QuotedRef =
        bySeq[seq]?.let { QuotedRef(seq, it.item.message.displayName, ReplyToken.strip(it.text)) }
            ?: QuotedRef(seq, null, null)

    // A "load older" header sits at LazyColumn index 0 when more history exists.
    val listOffset = if (state.hasMoreHistory) 1 else 0
    fun rowIndexForSeq(seq: Long): Int? =
        rows.indexOfFirst { it is ConvRow.Msg && it.item.message.seq == seq }
            .takeIf { it >= 0 }?.plus(listOffset)

    // Scroll to the newest row when the tail changes (a new message), not on
    // history pagination.
    val tailKey = rows.lastOrNull { it !is ConvRow.Pending }?.key
    LaunchedEffect(tailKey) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex + listOffset)
    }

    val lastListIndex = rows.lastIndex + listOffset
    val atBottom by remember(lastListIndex) {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= lastListIndex.coerceAtLeast(0)
        }
    }

    val isSystem = group?.kind == GroupKind.SYSTEM
    val currentIsAdmin = group?.isAdmin == true
    val minToPost = maxOf(group?.limits?.minMembersToPost ?: 0, 3)
    val memberCount = group?.memberCount ?: 0
    val whoCanPost = group?.settings?.whoCanPost ?: WhoCanPost.EVERYONE

    val postBlockedReason: String? = when {
        isSystem -> stringResource(R.string.messages_system_readonly)
        memberCount < minToPost -> stringResource(R.string.messages_too_small, minToPost)
        whoCanPost == WhoCanPost.ADMINS && !currentIsAdmin -> stringResource(R.string.messages_cannot_post)
        groupCrypto.enabled && groupCrypto.activeKey == null -> stringResource(R.string.enc_error_locked)
        else -> null
    }
    val encrypted = groupCrypto.enabled && groupCrypto.activeKey != null
    fun projectedLength(fullPlaintext: String): Int =
        if (encrypted) {
            com.sh7411usa.shliachtzibbur.core.crypto.MessageCrypto.projectedCipherLength(fullPlaintext)
        } else {
            fullPlaintext.length
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = threadQuery,
                            onValueChange = viewModel::setThreadQuery,
                            placeholder = { Text(stringResource(R.string.search_in_thread)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (groupCrypto.enabled) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = stringResource(R.string.enc_badge_secure),
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(18.dp),
                                )
                            }
                            Text(
                                if (isSystem) stringResource(R.string.group_system_name) else group?.name.orEmpty(),
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchOpen) {
                            searchOpen = false
                            viewModel.setThreadQuery("")
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) viewModel.setThreadQuery("") }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search_in_thread),
                        )
                    }
                    IconButton(onClick = { onOpenSettings(viewModel.groupId) }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.group_settings_title),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!locked) {
                MessageInputBar(
                    enabled = postBlockedReason == null,
                    blockedReason = postBlockedReason,
                    sending = state.sending,
                    hardLimit = maxMessageChars,
                    encrypted = encrypted,
                    projectedLength = ::projectedLength,
                    replyingTo = replyToSeq?.let { quotedFor(it) },
                    onCancelReply = { replyToSeq = null },
                    onSend = viewModel::send,
                    onCreatePoll = { showPollComposer = true },
                )
            }
        },
        floatingActionButton = {
            if (isTouch && !locked && !atBottom && rows.isNotEmpty()) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(lastListIndex) } },
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.messages_scroll_to_latest),
                    )
                }
            }
        },
    ) { padding ->
        if (locked) {
            EncryptionLockPanel(
                rejected = state.keyRejected,
                onSubmitKey = { viewModel.submitKey(it) },
                onClearRejected = viewModel::clearKeyRejected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.error?.let {
                Text(
                    it.toUserMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            derived.pinnedSeq?.let { pin ->
                val pinnedRow = bySeq[pin]
                PinnedBanner(
                    text = pinnedRow?.let { it.poll?.let { p -> "📊 " + p.question } ?: it.text }
                        ?: stringResource(R.string.pin_banner_unavailable),
                    canUnpin = selfIsAdmin,
                    onTap = {
                        val idx = rowIndexForSeq(pin)
                        if (idx != null) scope.launch { listState.animateScrollToItem(idx) }
                        else viewModel.loadOlder()
                    },
                    onUnpin = { viewModel.unpin(pin) },
                )
            }

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.messages_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    if (state.hasMoreHistory) {
                        item(key = "load-older") {
                            TextButton(
                                onClick = viewModel::loadOlder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                            ) {
                                Text(stringResource(R.string.messages_load_older))
                            }
                        }
                    }
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ConvRow.Control -> ControlTag(row.kind, row.actor)

                            is ConvRow.Summary -> PollSummaryRow(
                                state = row.state,
                                onJumpToPoll = {
                                    rowIndexForSeq(row.state.pollSeq)?.let { i ->
                                        scope.launch { listState.animateScrollToItem(i) }
                                    }
                                },
                            )

                            is ConvRow.Pending -> PendingBubble(
                                text = row.outbox.text,
                                failed = row.outbox.state == OutboxState.FAILED,
                                onRetry = { viewModel.retry(row.outbox.clientMessageId) },
                                onDelete = { viewModel.deleteFailed(row.outbox.clientMessageId) },
                            )

                            is ConvRow.Msg -> {
                                val m = row.item.message
                                val isSelf = m.senderId == selfId
                                if (row.poll != null) {
                                    PollCard(
                                        state = row.poll,
                                        actorName = m.displayName,
                                        isSelf = isSelf,
                                        isAuthor = m.senderId != null && m.senderId == row.poll.authorId,
                                        canReply = postBlockedReason == null,
                                        canPin = selfIsAdmin,
                                        isPinned = derived.pinnedSeq == m.seq,
                                        onVote = { choice -> viewModel.vote(m.seq, choice) },
                                        onEndPoll = { viewModel.endPoll(m.seq) },
                                        onPin = { viewModel.pin(m.seq) },
                                        onUnpin = { viewModel.unpin(m.seq) },
                                    )
                                } else if (row.sticker) {
                                    StickerBubble(
                                        emoji = row.text,
                                        isSelf = isSelf,
                                        canReply = postBlockedReason == null,
                                        canPin = selfIsAdmin,
                                        isPinned = derived.pinnedSeq == m.seq,
                                        reactions = reactionsBySeq[m.seq].orEmpty(),
                                        onReply = { replyToSeq = m.seq },
                                        onReact = { e -> viewModel.react(m.seq, e) },
                                        onPin = { viewModel.pin(m.seq) },
                                        onUnpin = { viewModel.unpin(m.seq) },
                                    )
                                } else {
                                    val reply = remember(row.text) { ReplyToken.parse(row.text) }
                                    MessageBubble(
                                        text = reply?.body ?: row.text,
                                        quoted = reply?.let { quotedFor(it.seq) },
                                        seq = m.seq,
                                        sender = m.displayName,
                                        isSelf = isSelf,
                                        isSystem = isSystem,
                                        markdown = settings.messagesMarkdown && !isSystem,
                                        showSeq = settings.showMessageSeq,
                                        canReply = postBlockedReason == null,
                                        canPin = selfIsAdmin,
                                        isPinned = derived.pinnedSeq == m.seq,
                                        security = row.item.security,
                                        original = m.text.takeIf {
                                            row.item.security == MessageSecurity.Secure ||
                                                row.item.security == MessageSecurity.Undecryptable
                                        },
                                        reactions = reactionsBySeq[m.seq].orEmpty(),
                                        onReply = { replyToSeq = m.seq },
                                        onReact = { e -> viewModel.react(m.seq, e) },
                                        onRequestKey = { showPasteKey = true },
                                        onPin = { viewModel.pin(m.seq) },
                                        onUnpin = { viewModel.unpin(m.seq) },
                                    )
                                }
                            }
                        }
                    }
                    if (groupCrypto.pendingAnnounce && currentIsAdmin) {
                        item(key = "enc-pending") { ControlTag(ControlKind.EncryptionPending, null) }
                    }
                }
            }
        }
    }

    if (showPollComposer) {
        PollComposerDialog(
            lengthOf = { q, opts -> projectedLength(com.sh7411usa.shliachtzibbur.core.util.PollSpec.format(q, opts)) },
            hardLimit = maxMessageChars,
            onCreate = { q, opts -> showPollComposer = false; viewModel.createPoll(q, opts) },
            onDismiss = { showPollComposer = false },
        )
    }

    if (showPasteKey) {
        KeyPromptDialog(
            title = stringResource(R.string.enc_paste_key_title),
            rejected = state.keyRejected,
            onSubmit = { viewModel.submitKey(it) },
            onDismiss = { showPasteKey = false; viewModel.clearKeyRejected() },
        )
    }
}

/** Centered grey tag for an in-band control message (encryption, pin, poll-end). */
@Composable
private fun ControlTag(kind: ControlKind, actor: String?) {
    val name = actor ?: stringResource(R.string.control_someone)
    val text = when (kind) {
        ControlKind.EncOn ->
            if (actor != null) stringResource(R.string.enc_tag_enabled, actor)
            else stringResource(R.string.enc_tag_enabled_generic)
        ControlKind.EncOff ->
            if (actor != null) stringResource(R.string.enc_tag_disabled, actor)
            else stringResource(R.string.enc_tag_disabled_generic)
        ControlKind.KeyChanged ->
            if (actor != null) stringResource(R.string.enc_tag_key_changed, actor)
            else stringResource(R.string.enc_tag_key_changed_generic)
        ControlKind.Pinned -> stringResource(R.string.pin_tag_pinned, name)
        ControlKind.Unpinned -> stringResource(R.string.pin_tag_unpinned, name)
        ControlKind.PollEnded -> stringResource(R.string.poll_tag_ended, name)
        ControlKind.EncryptionPending -> stringResource(R.string.enc_pending_note)
    }
    val icon = if (kind == ControlKind.Pinned || kind == ControlKind.Unpinned) Icons.Filled.Star else Icons.Filled.Lock
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Banner above the thread showing the currently pinned message. */
@Composable
private fun PinnedBanner(text: String, canUnpin: Boolean, onTap: () -> Unit, onUnpin: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .focusHighlight(makeFocusable = true)
                .clickable(onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    stringResource(R.string.pin_banner_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canUnpin) {
                IconButton(
                    onClick = onUnpin,
                    modifier = Modifier.focusHighlight(makeFocusable = true),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pin_action_unpin))
                }
            }
        }
    }
}

/** A message that is just an emoji — rendered big, no bubble. Still long-pressable. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerBubble(
    emoji: String,
    isSelf: Boolean,
    canReply: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    reactions: List<Reaction>,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    val isTouch = rememberIsTouchDevice()
    var menuOpen by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
    ) {
        Box {
            Text(
                emoji,
                fontSize = 52.sp,
                modifier = Modifier
                    .focusHighlight(makeFocusable = true)
                    .combinedClickable(
                        onClick = { if (!isTouch) menuOpen = true },
                        onLongClick = { menuOpen = true },
                    )
                    .padding(4.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canReply) {
                    QuickReactionRow(
                        onPick = { menuOpen = false; onReact(it) },
                        onMore = { menuOpen = false; showEmojiPicker = true },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.messages_reply)) },
                        onClick = { menuOpen = false; onReply() },
                    )
                }
                if (canPin) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isPinned) R.string.pin_action_unpin else R.string.pin_action_pin)) },
                        onClick = { menuOpen = false; if (isPinned) onUnpin() else onPin() },
                    )
                }
            }
        }
        if (reactions.isNotEmpty()) ReactionBar(reactions = reactions, alignEnd = isSelf)
    }
    if (showEmojiPicker) {
        EmojiPickerDialog(
            onPick = { showEmojiPicker = false; onReact(it) },
            onDismiss = { showEmojiPicker = false },
        )
    }
}

/** Interactive poll card. Results stay hidden until the viewer votes or the poll closes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PollCard(
    state: com.sh7411usa.shliachtzibbur.core.util.PollState,
    actorName: String?,
    isSelf: Boolean,
    isAuthor: Boolean,
    canReply: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    onVote: (Int) -> Unit,
    onEndPoll: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    val isTouch = rememberIsTouchDevice()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
    ) {
        Box {
            Column(
                Modifier
                    .widthIn(max = 340.dp)
                    .focusHighlight(makeFocusable = true)
                    .combinedClickable(
                        onClick = { if (!isTouch) menuOpen = true },
                        onLongClick = { menuOpen = true },
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text(
                    stringResource(R.string.poll_started_by, actorName ?: stringResource(R.string.control_someone)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(state.question, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                state.options.forEachIndexed { i, opt ->
                    val choice = i + 1
                    val count = state.counts.getOrElse(i) { 0 }
                    val pct = if (state.totalVotes > 0) count * 100 / state.totalVotes else 0
                    if (state.showResults) {
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Row {
                                Text(
                                    (if (state.myChoice == choice) "✓ " else "") + opt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("$count · $pct%", style = MaterialTheme.typography.labelSmall)
                            }
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { if (state.totalVotes > 0) count.toFloat() / state.totalVotes else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                            )
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .focusHighlight(makeFocusable = true)
                                .clickable { onVote(choice) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(opt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                Text(
                    if (state.ended) stringResource(R.string.poll_closed)
                    else stringResource(R.string.poll_votes, state.totalVotes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (isAuthor && !state.ended) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.poll_end)) },
                        onClick = { menuOpen = false; confirmEnd = true },
                    )
                }
                if (canPin) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isPinned) R.string.pin_action_unpin else R.string.pin_action_pin)) },
                        onClick = { menuOpen = false; if (isPinned) onUnpin() else onPin() },
                    )
                }
            }
        }
    }
    if (confirmEnd) {
        ConfirmDialog(
            text = stringResource(R.string.poll_end_confirm),
            confirmLabel = stringResource(R.string.poll_end),
            onConfirm = { confirmEnd = false; onEndPoll() },
            onDismiss = { confirmEnd = false },
            destructive = true,
        )
    }
}

/** Final results of a closed poll, at its close position, with a jump-to-poll arrow. */
@Composable
private fun PollSummaryRow(
    state: com.sh7411usa.shliachtzibbur.core.util.PollState,
    onJumpToPoll: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        IconButton(
            onClick = onJumpToPoll,
            modifier = Modifier.focusHighlight(makeFocusable = true),
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.poll_jump_to_poll))
        }
        Column(
            Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(stringResource(R.string.poll_results_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(state.question, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            state.options.forEachIndexed { i, opt ->
                val count = state.counts.getOrElse(i) { 0 }
                Column(Modifier.padding(vertical = 2.dp)) {
                    Row {
                        Text(opt, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (state.totalVotes > 0) count.toFloat() / state.totalVotes else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.poll_total_votes, state.totalVotes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Compose a new poll: a question and 2+ options. */
@Composable
private fun PollComposerDialog(
    lengthOf: (String, List<String>) -> Int,
    hardLimit: Int,
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    var options by rememberSaveable { mutableStateOf(listOf("", "")) }
    val cleaned = options.map { it.trim() }.filter { it.isNotEmpty() }
    val projected = if (question.isNotBlank() && cleaned.size >= 2) lengthOf(question, cleaned) else 0
    val overLimit = projected > hardLimit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.poll_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.poll_question)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { i, opt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = opt,
                            onValueChange = { v -> options = options.toMutableList().also { it[i] = v } },
                            label = { Text(stringResource(R.string.poll_option, i + 1)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp),
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options = options.toMutableList().also { it.removeAt(i) } }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
                TextButton(onClick = { options = options + "" }) {
                    Text(stringResource(R.string.poll_add_option))
                }
                if (projected > 0) {
                    Text(
                        stringResource(R.string.messages_char_count, projected, hardLimit),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(question.trim(), cleaned) },
                enabled = question.isNotBlank() && cleaned.size >= 2 && !overLimit,
            ) { Text(stringResource(R.string.poll_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Full-screen gate shown when an encrypted group can't be read with the known keys. */
@Composable
private fun EncryptionLockPanel(
    rejected: Boolean,
    onSubmitKey: (String) -> Unit,
    onClearRejected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hex by rememberSaveable { mutableStateOf("") }
    Column(
        modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.enc_lock_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.enc_lock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedTextField(
            value = hex,
            onValueChange = { hex = KeyHex.clean(it); if (rejected) onClearRejected() },
            singleLine = true,
            isError = rejected,
            placeholder = { Text(stringResource(R.string.enc_lock_hint)) },
            supportingText = if (rejected) {
                { Text(stringResource(R.string.enc_key_wrong)) }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )
        TextButton(
            onClick = { onSubmitKey(hex) },
            enabled = hex.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.enc_unlock)) }
    }
}

/** Small dialog to paste another key when a message won't decrypt. */
@Composable
private fun KeyPromptDialog(
    title: String,
    rejected: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var hex by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = hex,
                onValueChange = { hex = KeyHex.clean(it) },
                singleLine = true,
                isError = rejected,
                placeholder = { Text(stringResource(R.string.enc_lock_hint)) },
                supportingText = if (rejected) {
                    { Text(stringResource(R.string.enc_key_wrong)) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(hex); hex = "" }, enabled = hex.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** Name + best phone number for a contact chosen via `ACTION_PICK`, as plain text. */
private fun readContactSnippet(context: Context, contactUri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val (name, contactId) = resolver.query(
        contactUri,
        arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts._ID),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1) else return@runCatching null
    } ?: return@runCatching null

    val number = resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
        ),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(contactId),
        null,
    )?.use { c ->
        if (c.moveToFirst()) c.getString(1)?.takeIf { it.isNotBlank() } ?: c.getString(0) else null
    }

    buildString {
        append(name.ifBlank { number.orEmpty() })
        if (!number.isNullOrBlank()) append('\n').append(number)
    }.takeIf { it.isNotBlank() }
}.getOrNull()

/** The referenced message for a reply preview. [sender]/[text] are null when it isn't loaded. */
private data class QuotedRef(val seq: Long, val sender: String?, val text: String?)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    text: String,
    quoted: QuotedRef?,
    seq: Long,
    sender: String?,
    isSelf: Boolean,
    isSystem: Boolean,
    markdown: Boolean,
    showSeq: Boolean,
    canReply: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    security: MessageSecurity,
    original: String?,
    reactions: List<Reaction>,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onRequestKey: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val isTouch = rememberIsTouchDevice()
    var menuOpen by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    if (isSystem) {
        MessageText(
            text = text,
            markdown = false,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
        )
        return
    }
    val bubbleColor =
        if (isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val onBubble =
        if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
    ) {
        if (showSeq) {
            Text(
                "#$seq",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Box {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .focusHighlight(makeFocusable = true)
                    .combinedClickable(
                        // On non-touch devices the D-pad centre key opens the menu
                        // (there is no long-press); on touch devices a tap is inert
                        // and the long-press opens it, as before.
                        onClick = { if (!isTouch) menuOpen = true },
                        onLongClick = { menuOpen = true },
                    )
                    .background(bubbleColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (!isSelf && sender != null) {
                    Text(
                        sender,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (quoted != null) {
                    QuotedPreview(quoted, onBubble)
                }
                if (security == MessageSecurity.Undecryptable) {
                    Text(
                        stringResource(R.string.enc_undecryptable_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onBubble.copy(alpha = 0.7f),
                    )
                } else {
                    MessageText(text = text, markdown = markdown, color = onBubble)
                }
                SecurityBadge(security = security, onBubble = onBubble, onTap = onRequestKey)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canReply) {
                    QuickReactionRow(
                        onPick = {
                            menuOpen = false
                            onReact(it)
                        },
                        onMore = {
                            menuOpen = false
                            showEmojiPicker = true
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.messages_reply)) },
                        onClick = {
                            menuOpen = false
                            onReply()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy)) },
                    onClick = {
                        clipboard.setText(AnnotatedString(text))
                        menuOpen = false
                    },
                )
                if (original != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.enc_view_original)) },
                        onClick = {
                            menuOpen = false
                            showOriginal = true
                        },
                    )
                }
                if (canPin) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isPinned) R.string.pin_action_unpin else R.string.pin_action_pin)) },
                        onClick = {
                            menuOpen = false
                            if (isPinned) onUnpin() else onPin()
                        },
                    )
                }
            }
        }
        if (reactions.isNotEmpty()) {
            ReactionBar(reactions = reactions, alignEnd = isSelf)
        }
    }

    if (showOriginal && original != null) {
        AlertDialog(
            onDismissRequest = { showOriginal = false },
            title = { Text(stringResource(R.string.enc_original_title)) },
            text = {
                Text(
                    original,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(original))
                    showOriginal = false
                }) { Text(stringResource(R.string.action_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { showOriginal = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onPick = {
                showEmojiPicker = false
                onReact(it)
            },
            onDismiss = { showEmojiPicker = false },
        )
    }
}

/** The one-tap reaction row shown at the top of a message's menu. */
@Composable
private fun QuickReactionRow(onPick: (String) -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Reactions.QUICK.forEach { emoji ->
            Box(
                Modifier
                    .size(40.dp)
                    .focusHighlight(makeFocusable = true)
                    .clickable { onPick(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .focusHighlight(makeFocusable = true)
                .clickable { onMore() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.messages_react_more),
            )
        }
    }
}

/** A full-screen-ish grid of emoji for the "more" reaction chooser. */
@Composable
private fun EmojiPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        title = { Text(stringResource(R.string.messages_react_pick)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Reactions.PALETTE.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { emoji ->
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .focusHighlight(makeFocusable = true)
                                    .clickable { onPick(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        },
    )
}

/**
 * Reaction badges under a bubble. Collapsed they show one chip per distinct
 * emoji with a count; tapping toggles a list of who reacted with what.
 */
@Composable
private fun ReactionBar(reactions: List<Reaction>, alignEnd: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val youLabel = stringResource(R.string.messages_react_you)
    val grouped = remember(reactions) {
        reactions.groupBy { it.emoji }.entries.toList()
    }
    Column(
        Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Row(
            Modifier
                .focusHighlight(makeFocusable = true)
                .clickable { expanded = !expanded }
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            grouped.forEach { (emoji, list) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            emoji,
                            fontSize = 13.sp,
                            color = if (list.any { it.pending }) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (list.size > 1) {
                            Text(
                                " ${list.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (expanded) {
            grouped.forEach { (emoji, list) ->
                val names = list.joinToString { it.reactor ?: youLabel }
                Text(
                    "$emoji  $names",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

/** Small lock/insecure marker under a message body. Undecryptable is tappable. */
@Composable
private fun SecurityBadge(security: MessageSecurity, onBubble: Color, onTap: () -> Unit) {
    when (security) {
        MessageSecurity.None -> Unit
        MessageSecurity.Secure -> Row(
            Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(R.string.enc_badge_secure),
                tint = onBubble.copy(alpha = 0.55f),
                modifier = Modifier.size(12.dp),
            )
        }
        MessageSecurity.Insecure -> Row(
            Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp),
            )
            Text(
                stringResource(R.string.enc_badge_insecure),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        MessageSecurity.Undecryptable -> Row(
            Modifier
                .padding(top = 4.dp)
                .focusHighlight(makeFocusable = true)
                .clickable { onTap() }
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp),
            )
            Text(
                stringResource(R.string.enc_badge_undecryptable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** WhatsApp/Telegram-style quoted preview of the message a reply points at. */
@Composable
private fun QuotedPreview(quoted: QuotedRef, onBubble: Color) {
    Row(
        Modifier
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(onBubble.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(onBubble.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                quoted.sender ?: stringResource(R.string.messages_reply_prefix, quoted.seq),
                style = MaterialTheme.typography.labelMedium,
                color = onBubble,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                quoted.text?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.messages_reply_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = onBubble.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PendingBubble(
    text: String,
    failed: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .then(
                        if (failed) {
                            Modifier
                                .focusHighlight(makeFocusable = true)
                                .clickable { menuOpen = true }
                        } else {
                            Modifier
                        },
                    )
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        if (failed) R.string.messages_send_failed else R.string.messages_sending,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.messages_retry)) },
                    onClick = {
                        menuOpen = false
                        onRetry()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.messages_delete_message),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInputBar(
    enabled: Boolean,
    blockedReason: String?,
    sending: Boolean,
    hardLimit: Int,
    encrypted: Boolean,
    projectedLength: (String) -> Int,
    replyingTo: QuotedRef?,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
    onCreatePoll: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var attachOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun fullPlaintext(body: String): String =
        replyingTo?.let { ReplyToken.format(it.seq, body) } ?: body

    // The composer shows the *wire* length (after the reply marker and, in an
    // encrypted group, encryption) against the server's 1000-char limit.
    val used = projectedLength(fullPlaintext(text.trim()))
    val overLimit = used > hardLimit

    fun append(snippet: String) {
        text = (if (text.isBlank()) "" else text.trimEnd() + "\n") + snippet
    }

    fun sendNow() {
        val body = text.trim()
        if (body.isNotEmpty() && enabled && !sending && !overLimit) {
            onSend(fullPlaintext(body))
            text = ""
            onCancelReply()
        }
    }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { readContactSnippet(context, it)?.let(::append) }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        com.sh7411usa.shliachtzibbur.core.util.LastLocation.geoUri(context)?.let(::append)
    }

    fun attachLocation() {
        val geo = com.sh7411usa.shliachtzibbur.core.util.LastLocation.geoUri(context)
        if (geo != null) append(geo)
        else locationPermission.launch(com.sh7411usa.shliachtzibbur.core.util.LastLocation.permissions)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (blockedReason != null) {
            Text(
                blockedReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        } else {
            if (replyingTo != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                R.string.messages_replying_to,
                                replyingTo.sender ?: "#${replyingTo.seq}",
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        replyingTo.text?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = onCancelReply,
                        modifier = Modifier.focusHighlight(makeFocusable = true),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
            }
            if (text.isNotEmpty()) {
                Text(
                    stringResource(R.string.messages_char_count, used, hardLimit),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(
                        onClick = { attachOpen = true },
                        modifier = Modifier.focusHighlight(makeFocusable = true),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.messages_attach),
                        )
                    }
                    DropdownMenu(expanded = attachOpen, onDismissRequest = { attachOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messages_attach_contact)) },
                            onClick = {
                                attachOpen = false
                                pickContact.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messages_attach_location)) },
                            onClick = {
                                attachOpen = false
                                attachLocation()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.poll_new_title)) },
                            onClick = {
                                attachOpen = false
                                onCreatePoll()
                            },
                        )
                    }
                }
                if (encrypted) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.enc_badge_secure),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(16.dp),
                    )
                }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    isError = overLimit,
                    placeholder = { Text(stringResource(R.string.messages_input_hint)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    // A single-tap "Send" on the keyboard (incl. D-pad OK on a
                    // T9 keypad) so the send button doesn't need to be focused.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { sendNow() }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                IconButton(
                    onClick = { sendNow() },
                    enabled = enabled && !sending && text.isNotBlank() && !overLimit,
                    modifier = Modifier.focusHighlight(makeFocusable = true),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send),
                    )
                }
            }
        }
    }
}
