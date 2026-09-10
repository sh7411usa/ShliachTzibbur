package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.model.WhoCanAddMembers
import com.sh7411usa.shliachtzibbur.core.model.WhoCanPost
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.ConfirmDialog
import com.sh7411usa.shliachtzibbur.ui.common.SecondaryButton
import com.sh7411usa.shliachtzibbur.ui.common.SectionHeader
import com.sh7411usa.shliachtzibbur.ui.common.SegmentedChoice
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    onBack: () -> Unit,
    onOpenMembers: (String) -> Unit,
    onOpenEncryption: (String) -> Unit,
    onLeftOrDeleted: () -> Unit,
    viewModel: GroupSettingsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val encrypted by viewModel.encryptionEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(state.left, state.deleted) {
        if (state.left || state.deleted) onLeftOrDeleted()
    }

    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var nameDraft by remember(group?.name) { mutableStateOf(group?.name.orEmpty()) }

    val isAdmin = group?.isAdmin == true
    val isSystem = group?.isSystem == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_settings_title)) },
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
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let {
                Text(
                    it.toUserMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (!isSystem) {
                SectionHeader(stringResource(R.string.group_settings_name))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(100) },
                        singleLine = true,
                        enabled = isAdmin,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { viewModel.rename(nameDraft) },
                        enabled = isAdmin && nameDraft.isNotBlank() && nameDraft != group?.name,
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }

            SectionHeader(stringResource(R.string.app_settings_section_notifications))
            SettingSwitchRow(
                label = stringResource(R.string.group_settings_mute),
                checked = group?.muted == true,
                onCheckedChange = viewModel::setMuted,
            )

            if (!isSystem) {
                SectionHeader(stringResource(R.string.group_settings_who_can_post))
                SegmentedChoice(
                    options = WhoCanPost.entries,
                    selected = group?.settings?.whoCanPost ?: WhoCanPost.EVERYONE,
                    label = {
                        stringResource(
                            if (it == WhoCanPost.EVERYONE) R.string.group_settings_everyone
                            else R.string.group_settings_admins,
                        )
                    },
                    onSelect = { if (isAdmin) viewModel.setWhoCanPost(it) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                SectionHeader(stringResource(R.string.group_settings_who_can_add))
                SegmentedChoice(
                    options = WhoCanAddMembers.entries,
                    selected = group?.settings?.whoCanAddMembers ?: WhoCanAddMembers.ADMINS,
                    label = {
                        stringResource(
                            if (it == WhoCanAddMembers.EVERYONE) R.string.group_settings_everyone
                            else R.string.group_settings_admins,
                        )
                    },
                    onSelect = { if (isAdmin) viewModel.setWhoCanAddMembers(it) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (!isAdmin) {
                    Text(
                        stringResource(R.string.group_settings_admin_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            ThinDivider()

            if (!isSystem) {
                TextButton(
                    onClick = { onOpenMembers(viewModel.groupId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.group_settings_members) +
                            "  ·  " + (group?.memberCount ?: 0),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = { onOpenEncryption(viewModel.groupId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.enc_section) + "  ·  " + stringResource(
                            if (encrypted) R.string.enc_state_on else R.string.enc_state_off,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isSystem) {
                    SecondaryButton(
                        text = stringResource(R.string.group_settings_leave),
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isAdmin && !isSystem) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.group_settings_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            text = stringResource(R.string.group_settings_leave_confirm),
            confirmLabel = stringResource(R.string.action_leave),
            onConfirm = { confirmLeave = false; viewModel.leave() },
            onDismiss = { confirmLeave = false },
            destructive = true,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            text = stringResource(R.string.group_settings_delete_confirm),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
