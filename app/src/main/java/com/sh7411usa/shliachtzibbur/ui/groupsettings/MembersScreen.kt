package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.model.Member
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    onBack: () -> Unit,
    viewModel: MembersViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val members by viewModel.members.collectAsStateWithLifecycle()
    val group by viewModel.group.collectAsStateWithLifecycle()
    val selfId by viewModel.selfUserId.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    val canAdd = group?.let { g ->
        g.isAdmin || g.settings.whoCanAddMembers == com.sh7411usa.shliachtzibbur.core.model.WhoCanAddMembers.EVERYONE
    } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.members_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (canAdd) {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.members_add))
                        }
                    }
                },
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
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(members, key = { it.userId }) { member ->
                    MemberRow(
                        member = member,
                        isSelf = member.userId == selfId,
                        canManage = group?.isAdmin == true && member.userId != selfId,
                        onMakeAdmin = { viewModel.setRole(member.userId, Role.ADMIN) },
                        onRemoveAdmin = { viewModel.setRole(member.userId, Role.MEMBER) },
                        onRemove = { viewModel.remove(member.userId) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddMembersDialog(
            working = state.working,
            result = state.addResult,
            onDismiss = {
                showAdd = false
                viewModel.clearAddResult()
            },
            onSubmit = { raw -> viewModel.addMembers(raw, null) },
        )
    }
}

@Composable
private fun MemberRow(
    member: Member,
    isSelf: Boolean,
    canManage: Boolean,
    onMakeAdmin: () -> Unit,
    onRemoveAdmin: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isSelf) "${member.displayName} · ${stringResource(R.string.members_you)}" else member.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    if (member.isAdmin) R.string.members_role_admin else R.string.members_role_member,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canManage) {
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.focusHighlight(makeFocusable = true),
                ) { Text(stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (member.isAdmin) R.string.members_remove_admin
                                    else R.string.members_make_admin,
                                ),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            if (member.isAdmin) onRemoveAdmin() else onMakeAdmin()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.members_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMembersDialog(
    working: Boolean,
    result: com.sh7411usa.shliachtzibbur.core.model.AddMembersResult?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.members_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.members_add_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let {
                    Text(
                        stringResource(
                            R.string.members_add_result,
                            it.added.size,
                            it.notFound.size,
                            it.alreadyMember.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(text) },
                enabled = !working && text.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
