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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.ConfirmDialog
import com.sh7411usa.shliachtzibbur.ui.common.PrimaryButton
import com.sh7411usa.shliachtzibbur.ui.common.SectionHeader
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEncryptionScreen(
    onBack: () -> Unit,
    viewModel: GroupEncryptionViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val crypto by viewModel.crypto.collectAsStateWithLifecycle()
    val keyError by viewModel.keyError.collectAsStateWithLifecycle()
    val isAdmin = group?.isAdmin == true

    var keyDraft by remember { mutableStateOf("") }
    var confirmEnable by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var confirmGenerate by remember { mutableStateOf(false) }
    var confirmDeleteKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enc_screen_title)) },
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.enc_toggle_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.enc_toggle_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = crypto.enabled,
                    enabled = isAdmin,
                    onCheckedChange = { on -> if (on) confirmEnable = true else confirmDisable = true },
                )
            }
            if (!isAdmin) {
                Text(
                    stringResource(R.string.group_settings_admin_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            ThinDivider()
            SectionHeader(stringResource(R.string.enc_keys_header))

            if (crypto.keys.isEmpty()) {
                Text(
                    stringResource(R.string.enc_lock_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            crypto.keys.asReversed().forEach { key ->
                KeyRow(
                    key = key,
                    isActive = key.id == (crypto.activeKey?.id),
                    canUse = isAdmin,
                    onUse = { viewModel.useKey(key.id) },
                    onDelete = { confirmDeleteKey = key.id },
                )
            }

            SectionHeader(stringResource(R.string.enc_add_key_label))
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it.trim(); if (keyError) viewModel.clearKeyError() },
                singleLine = true,
                isError = keyError,
                placeholder = { Text(stringResource(R.string.enc_add_key_hint)) },
                supportingText = if (keyError) {
                    { Text(stringResource(R.string.enc_add_key_invalid)) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            TextButton(
                onClick = {
                    viewModel.addKey(keyDraft)
                    keyDraft = ""
                },
                enabled = keyDraft.isNotBlank(),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.action_add)) }

            if (isAdmin) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = stringResource(R.string.enc_generate_key),
                    onClick = { confirmGenerate = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmEnable) {
        ConfirmDialog(
            text = stringResource(R.string.enc_enable_confirm),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = { confirmEnable = false; viewModel.enableEncryption() },
            onDismiss = { confirmEnable = false },
        )
    }
    if (confirmDisable) {
        ConfirmDialog(
            text = stringResource(R.string.enc_disable_confirm),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = { confirmDisable = false; viewModel.disableEncryption() },
            onDismiss = { confirmDisable = false },
            destructive = true,
        )
    }
    if (confirmGenerate) {
        ConfirmDialog(
            text = stringResource(R.string.enc_generate_confirm),
            confirmLabel = stringResource(R.string.enc_generate_key),
            onConfirm = { confirmGenerate = false; viewModel.changeGroupKey() },
            onDismiss = { confirmGenerate = false },
            destructive = true,
        )
    }
    confirmDeleteKey?.let { keyId ->
        ConfirmDialog(
            text = stringResource(R.string.enc_delete_key_confirm),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDeleteKey = null; viewModel.removeKey(keyId) },
            onDismiss = { confirmDeleteKey = null },
            destructive = true,
        )
    }
}

@Composable
private fun KeyRow(
    key: GroupKey,
    isActive: Boolean,
    canUse: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                key.hex.take(8) + "…" + key.hex.takeLast(4),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            if (isActive) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.enc_key_current)) },
                )
            }
        }
        if (canUse && !isActive) {
            TextButton(onClick = onUse) { Text(stringResource(R.string.enc_action_use)) }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
