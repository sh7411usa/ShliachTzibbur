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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.GroupKind
import com.sh7411usa.shliachtzibbur.core.model.MessageSecurity
import com.sh7411usa.shliachtzibbur.core.model.OutboxState
import com.sh7411usa.shliachtzibbur.core.model.ServiceMessage
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost
import com.sh7411usa.shliachtzibbur.core.util.Reactions
import com.sh7411usa.shliachtzibbur.core.util.ReplyToken
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.MessageText
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight
import com.sh7411usa.shliachtzibbur.ui.common.rememberIsTouchDevice
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage

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
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var replyToSeq by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPasteKey by rememberSaveable { mutableStateOf(false) }

    val locked = lockState == LockState.NeedsKey

    val listState = rememberLazyListState()

    // Emoji reactions are pulled out of the message stream and hung on the
    // message each one targets; everything else stays a normal row.
    val (visibleItems, reactionsBySeq) = remember(items) { splitReactions(items) }

    // Resolve reply markers against the messages currently in the thread.
    val deliveredBySeq = remember(visibleItems) {
        visibleItems.asSequence()
            .filterIsInstance<ConversationItem.Delivered>()
            .associateBy { it.message.seq }
    }
    fun quotedFor(seq: Long): QuotedRef =
        deliveredBySeq[seq]?.let { QuotedRef(seq, it.message.displayName, ReplyToken.strip(it.displayText)) }
            ?: QuotedRef(seq, null, null)

    // Scroll to the newest message only when the tail changes (a new message),
    // not when older history is prepended by pagination.
    val tailKey = visibleItems.lastOrNull()?.let { it.key() }
    LaunchedEffect(tailKey) {
        if (visibleItems.isNotEmpty()) listState.animateScrollToItem(visibleItems.lastIndex)
    }

    val isSystem = group?.kind == GroupKind.SYSTEM
    val currentIsAdmin = group?.isAdmin == true
    val minToPost = group?.limits?.minMembersToPost ?: 0
    val memberCount = group?.memberCount ?: 0
    val whoCanPost = group?.settings?.whoCanPost ?: WhoCanPost.EVERYONE

    val postBlockedReason: String? = when {
        isSystem -> stringResource(R.string.messages_system_readonly)
        memberCount < minToPost -> stringResource(R.string.messages_too_small, minToPost)
        whoCanPost == WhoCanPost.ADMINS && !currentIsAdmin -> stringResource(R.string.messages_cannot_post)
        groupCrypto.enabled && groupCrypto.activeKey == null -> stringResource(R.string.enc_error_locked)
        else -> null
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
                        Text(
                            if (isSystem) stringResource(R.string.group_system_name) else group?.name.orEmpty(),
                            maxLines = 1,
                        )
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
                    maxLength = maxMessageChars,
                    encrypted = groupCrypto.enabled && groupCrypto.activeKey != null,
                    replyingTo = replyToSeq?.let { quotedFor(it) },
                    onCancelReply = { replyToSeq = null },
                    onSend = viewModel::send,
                )
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
            if (visibleItems.isEmpty()) {
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
                    items(visibleItems, key = { it.key() }) { item ->
                        when (item) {
                            is ConversationItem.Delivered -> {
                                val serviceKind = remember(item.displayText) {
                                    ServiceMessage.parse(item.displayText)
                                }
                                if (serviceKind != null) {
                                    ServiceTag(serviceKind, item.message.displayName)
                                } else {
                                    val body = item.displayText
                                    val reply = remember(body) { ReplyToken.parse(body) }
                                    MessageBubble(
                                        text = reply?.body ?: body,
                                        quoted = reply?.let { quotedFor(it.seq) },
                                        seq = item.message.seq,
                                        sender = item.message.displayName,
                                        isSelf = item.message.senderId == selfId,
                                        isSystem = isSystem,
                                        markdown = settings.messagesMarkdown && !isSystem,
                                        showSeq = settings.showMessageSeq,
                                        canReply = postBlockedReason == null,
                                        security = item.security,
                                        original = item.message.text.takeIf {
                                            item.security == MessageSecurity.Secure ||
                                                item.security == MessageSecurity.Undecryptable
                                        },
                                        reactions = reactionsBySeq[item.message.seq].orEmpty(),
                                        onReply = { replyToSeq = item.message.seq },
                                        onReact = { emoji -> viewModel.react(item.message.seq, emoji) },
                                        onRequestKey = { showPasteKey = true },
                                    )
                                }
                            }

                            is ConversationItem.Pending -> PendingBubble(
                                text = item.outbox.text,
                                failed = item.outbox.state == OutboxState.FAILED,
                                onRetry = { viewModel.retry(item.outbox.clientMessageId) },
                                onDelete = { viewModel.deleteFailed(item.outbox.clientMessageId) },
                            )
                        }
                    }
                }
            }
        }
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

private fun ConversationItem.key(): String = when (this) {
    is ConversationItem.Delivered -> "d-${message.id}"
    is ConversationItem.Pending -> "p-${outbox.clientMessageId}"
}

/** Centered grey tag for an in-band encryption service message. */
@Composable
private fun ServiceTag(kind: ServiceMessage, actor: String?) {
    val text = when (kind) {
        ServiceMessage.EncryptionOn ->
            if (actor != null) stringResource(R.string.enc_tag_enabled, actor)
            else stringResource(R.string.enc_tag_enabled_generic)
        ServiceMessage.EncryptionOff ->
            if (actor != null) stringResource(R.string.enc_tag_disabled, actor)
            else stringResource(R.string.enc_tag_disabled_generic)
        ServiceMessage.KeyChanged ->
            if (actor != null) stringResource(R.string.enc_tag_key_changed, actor)
            else stringResource(R.string.enc_tag_key_changed_generic)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Lock,
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
            onValueChange = { hex = it.trim(); if (rejected) onClearRejected() },
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
                onValueChange = { hex = it.trim() },
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

/** One emoji reaction shown on the message it targets. */
private data class Reaction(
    val emoji: String,
    /** The reactor's display name; null while the send is still pending. */
    val reactor: String?,
    /** The reactor's user id, used to keep only their most recent reaction. */
    val reactorId: String?,
    /** Reaction-message seq; [Long.MAX_VALUE] while pending. */
    val seq: Long,
    val pending: Boolean,
)

private data class SplitConversation(
    val items: List<ConversationItem>,
    val reactions: Map<Long, List<Reaction>>,
)

/**
 * Splits [raw] into the rows that get their own bubble and a `targetSeq -> reactions`
 * map. Since messages can't be un-sent, only each person's most recent reaction to
 * a given message is kept.
 */
private fun splitReactions(raw: List<ConversationItem>): SplitConversation {
    val visible = ArrayList<ConversationItem>(raw.size)
    val byTarget = LinkedHashMap<Long, MutableList<Reaction>>()

    fun add(target: Long, reaction: Reaction) {
        byTarget.getOrPut(target) { mutableListOf() }.add(reaction)
    }

    for (item in raw) {
        when (item) {
            is ConversationItem.Delivered -> {
                val target = Reactions.targetOf(item.displayText)
                val emoji = Reactions.of(item.displayText)
                if (target != null && emoji != null) {
                    add(
                        target,
                        Reaction(emoji, item.message.displayName, item.message.senderId, item.message.seq, pending = false),
                    )
                } else {
                    visible.add(item)
                }
            }

            is ConversationItem.Pending -> {
                val target = Reactions.targetOf(item.outbox.text)
                val emoji = Reactions.of(item.outbox.text)
                if (target != null && emoji != null) {
                    add(target, Reaction(emoji, null, null, Long.MAX_VALUE, pending = true))
                } else {
                    visible.add(item)
                }
            }
        }
    }

    val collapsed = byTarget.mapValues { (_, list) ->
        list.groupBy { it.reactorId ?: "pending:${it.emoji}" }
            .map { (_, perPerson) -> perPerson.maxByOrNull { it.seq }!! }
            .sortedBy { it.seq }
    }
    return SplitConversation(visible, collapsed)
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
    security: MessageSecurity,
    original: String?,
    reactions: List<Reaction>,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onRequestKey: () -> Unit,
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
    maxLength: Int,
    encrypted: Boolean,
    replyingTo: QuotedRef?,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var attachOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // A reply still costs its hidden `RE:<seq> ` marker against the length limit.
    val tokenOverhead = replyingTo?.let { ReplyToken.format(it.seq, "").length } ?: 0
    val bodyMax = (maxLength - tokenOverhead).coerceAtLeast(0)
    val used = text.length + tokenOverhead
    LaunchedEffect(bodyMax) {
        if (text.length > bodyMax) text = text.take(bodyMax)
    }

    fun append(snippet: String) {
        val joined = (if (text.isBlank()) "" else text.trimEnd() + "\n") + snippet
        text = joined.take(bodyMax)
    }

    fun sendNow() {
        val body = text.trim()
        if (body.isNotEmpty() && enabled && !sending) {
            onSend(replyingTo?.let { ReplyToken.format(it.seq, body) } ?: body)
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
                    stringResource(R.string.messages_char_count, used, maxLength),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (used >= maxLength) {
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
                    onValueChange = { if (it.length <= bodyMax) text = it },
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
                    enabled = enabled && !sending && text.isNotBlank(),
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
