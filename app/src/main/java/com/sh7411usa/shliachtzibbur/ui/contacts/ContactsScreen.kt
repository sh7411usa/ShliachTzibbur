package com.sh7411usa.shliachtzibbur.ui.contacts

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.data.repo.DeviceContact
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.EmptyState
import com.sh7411usa.shliachtzibbur.ui.common.LoadingBox
import com.sh7411usa.shliachtzibbur.ui.common.SecondaryButton
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onNewGroupWith: (String) -> Unit,
    viewModel: ContactsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
            when {
                state.needsPermission -> EmptyState(
                    title = stringResource(R.string.contacts_title),
                    body = stringResource(R.string.contacts_permission_rationale),
                    action = {
                        SecondaryButton(
                            text = stringResource(R.string.action_allow),
                            onClick = { permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS) },
                        )
                    },
                )

                state.loading -> LoadingBox()

                state.contacts.isEmpty() -> EmptyState(
                    title = stringResource(R.string.contacts_empty),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.contacts, key = { it.e164 }) { contact ->
                        val inGroups = state.membership[contact.e164].orEmpty()
                        ContactRow(
                            contact = contact,
                            groupsIn = state.groups.filter { it.id in inGroups },
                            groupsToAdd = state.groups.filter { !it.isSystem && it.id !in inGroups },
                            onOpenGroup = onOpenGroup,
                            onAddToGroup = { groupId -> viewModel.addToGroup(contact.e164, groupId) },
                            onNewGroup = { onNewGroupWith(contact.e164) },
                        )
                        ThinDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: DeviceContact,
    groupsIn: List<com.sh7411usa.shliachtzibbur.core.model.Group>,
    groupsToAdd: List<com.sh7411usa.shliachtzibbur.core.model.Group>,
    onOpenGroup: (String) -> Unit,
    onAddToGroup: (String) -> Unit,
    onNewGroup: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .focusHighlight(makeFocusable = true)
                .clickable {
                    if (contact.isRegistered) expanded = !expanded else showInvite = true
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (contact.isRegistered) stringResource(R.string.contacts_on_tzibbur) else contact.e164,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!contact.isRegistered) {
                TextButton(onClick = { showInvite = true }) {
                    Text(stringResource(R.string.contacts_invite))
                }
            }
        }

        AnimatedVisibility(visible = expanded && contact.isRegistered) {
            Column(Modifier.padding(start = 24.dp, end = 16.dp, bottom = 8.dp)) {
                if (groupsIn.isNotEmpty()) {
                    Text(
                        stringResource(R.string.contacts_in_groups),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    groupsIn.forEach { g ->
                        Text(
                            g.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusHighlight(makeFocusable = true)
                                .clickable { onOpenGroup(g.id) }
                                .padding(vertical = 8.dp),
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.contacts_not_in_any_group),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(onClick = { showAdd = !showAdd }) {
                    Text(stringResource(R.string.contacts_add_to_group))
                }
                AnimatedVisibility(visible = showAdd) {
                    Column {
                        groupsToAdd.forEach { g ->
                            Text(
                                g.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusHighlight(makeFocusable = true)
                                    .clickable {
                                        showAdd = false
                                        onAddToGroup(g.id)
                                    }
                                    .padding(vertical = 8.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.contacts_new_group),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusHighlight(makeFocusable = true)
                                .clickable {
                                    showAdd = false
                                    onNewGroup()
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showInvite) {
        val default = stringResource(R.string.invite_blurb)
        var blurb by remember { mutableStateOf(default) }
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text(stringResource(R.string.contacts_invite_title)) },
            text = {
                OutlinedTextField(
                    value = blurb,
                    onValueChange = { blurb = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showInvite = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, blurb)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.contacts_invite_send)) }
            },
            dismissButton = {
                TextButton(onClick = { showInvite = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
