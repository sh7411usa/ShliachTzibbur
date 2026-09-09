package com.sh7411usa.shliachtzibbur.ui.usersettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.sh7411usa.shliachtzibbur.core.model.Device
import com.sh7411usa.shliachtzibbur.core.model.LegalKind
import com.sh7411usa.shliachtzibbur.core.util.Timestamps
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.ConfirmDialog
import com.sh7411usa.shliachtzibbur.ui.common.LoadingBox
import com.sh7411usa.shliachtzibbur.ui.common.SectionHeader
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsScreen(
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenLegal: (String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: UserSettingsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var nameDraft by remember(state.user?.displayName) { mutableStateOf(state.user?.displayName.orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_settings_title)) },
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
            SectionHeader(stringResource(R.string.user_settings_display_name))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(64) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.updateDisplayName(nameDraft) },
                    enabled = !state.savingName && nameDraft.isNotBlank() && nameDraft != state.user?.displayName,
                ) { Text(stringResource(R.string.action_save)) }
            }
            state.error?.let {
                Text(
                    it.detail ?: stringResource(R.string.error_generic),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SectionHeader(stringResource(R.string.user_settings_phone))
            ValueRow(state.user?.phoneE164 ?: "—")

            SectionHeader(stringResource(R.string.user_settings_email))
            ValueRow(state.user?.email ?: stringResource(R.string.user_settings_coming_soon))

            SectionHeader(stringResource(R.string.user_settings_google))
            ValueRow(
                if (state.user?.googleLinked == true) "✓"
                else stringResource(R.string.user_settings_coming_soon),
            )

            ThinDivider(Modifier.padding(vertical = 12.dp))

            NavRow(stringResource(R.string.user_settings_devices), onOpenDevices)
            NavRow(stringResource(R.string.user_settings_privacy)) { onOpenLegal(LegalKind.PRIVACY.slug) }
            NavRow(stringResource(R.string.user_settings_terms)) { onOpenLegal(LegalKind.TERMS.slug) }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.action_sign_out), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            text = stringResource(R.string.action_sign_out) + "?",
            confirmLabel = stringResource(R.string.action_sign_out),
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
                onSignedOut()
            },
            onDismiss = { confirmSignOut = false },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: UserSettingsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadDevices() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_settings_devices)) },
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
        val devices = state.devices
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = state.devicesLoading,
            onRefresh = { viewModel.loadDevices() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (devices == null && state.devicesLoading) {
                LoadingBox()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (state.deviceRemovalUnsupported) {
                        item {
                            Text(
                                stringResource(R.string.devices_removal_unsupported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(devices.orEmpty(), key = { it.id }) { device ->
                        DeviceRow(
                            device = device,
                            isCurrent = device.id == state.currentDeviceId,
                            canRemove = device.id != state.currentDeviceId && !state.deviceRemovalUnsupported,
                            onRemove = { viewModel.removeDevice(device.id) },
                        )
                        ThinDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: Device,
    isCurrent: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.deviceModel.ifBlank { device.platform } +
                    if (isCurrent) " · " + stringResource(R.string.user_settings_this_device) else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            val lastSeen = Timestamps.formatDate(device.lastSeenAt)
            if (lastSeen.isNotEmpty()) {
                Text(
                    lastSeen,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canRemove) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    kind: LegalKind,
    onBack: () -> Unit,
    viewModel: UserSettingsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(kind) { viewModel.loadLegal(kind) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (kind == LegalKind.PRIVACY) R.string.user_settings_privacy
                            else R.string.user_settings_terms,
                        ),
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
            )
        },
    ) { padding ->
        when {
            state.legalLoading || state.legal == null -> LoadingBox(Modifier.padding(padding))
            else -> com.sh7411usa.shliachtzibbur.ui.common.MarkdownText(
                markdown = state.legal!!.text,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun ValueRow(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
