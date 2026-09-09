package com.sh7411usa.shliachtzibbur.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.GroupKind
import com.sh7411usa.shliachtzibbur.core.model.OutboxState
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight
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

    val listState = rememberLazyListState()

    // Scroll to the newest message only when the tail changes (a new message),
    // not when older history is prepended by pagination.
    val tailKey = items.lastOrNull()?.let { it.key() }
    LaunchedEffect(tailKey) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
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
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSystem) stringResource(R.string.group_system_name) else group?.name.orEmpty(),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
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
            MessageInputBar(
                enabled = postBlockedReason == null,
                blockedReason = postBlockedReason,
                sending = state.sending,
                maxLength = group?.limits?.messageMaxLength ?: 1000,
                onSend = viewModel::send,
            )
        },
    ) { padding ->
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
            if (items.isEmpty()) {
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
                    items(items, key = { it.key() }) { item ->
                        when (item) {
                            is ConversationItem.Delivered -> MessageBubble(
                                text = item.message.text,
                                sender = item.message.displayName,
                                isSelf = item.message.senderId == selfId,
                                isSystem = isSystem,
                            )

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
}

private fun ConversationItem.key(): String = when (this) {
    is ConversationItem.Delivered -> "d-${message.id}"
    is ConversationItem.Pending -> "p-${outbox.clientMessageId}"
}

@Composable
private fun MessageBubble(text: String, sender: String?, isSelf: Boolean, isSystem: Boolean) {
    if (isSystem) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
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
            Text(text, style = MaterialTheme.typography.bodyLarge, color = onBubble)
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
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    fun sendNow() {
        val toSend = text.trim()
        if (toSend.isNotEmpty() && enabled && !sending) {
            onSend(toSend)
            text = ""
        }
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = text,
                    onValueChange = { if (it.length <= maxLength) text = it },
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
