package com.sh7411usa.shliachtzibbur.ui.groupsettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.util.DeviceInfo
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.LoadingBox
import com.sh7411usa.shliachtzibbur.ui.common.SecondaryButton
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMembersScreen(
    onDone: () -> Unit,
    viewModel: AddMembersViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var manual by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.setRegion(DeviceInfo.defaultRegion(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    val keySmsBody = if (state.keyHex != null) {
        stringResource(R.string.enc_key_sms, state.groupName, state.keyHex!!)
    } else {
        ""
    }
    LaunchedEffect(state.result) {
        if (state.result != null) {
            val targets = state.keyShareTargets
            if (targets.isNotEmpty()) {
                runCatching {
                    val to = targets.joinToString(",") { it.e164 }
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_SENDTO,
                            android.net.Uri.parse("smsto:$to"),
                        ).putExtra("sms_body", keySmsBody),
                    )
                }
                viewModel.clearKeyShareTargets()
            }
            onDone()
        }
    }

    fun commitManual() {
        if (manual.isNotBlank()) {
            viewModel.addTypedNumber(manual)
            manual = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_members_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::submit,
                        enabled = state.totalToAdd > 0 && !state.working,
                    ) {
                        Text(stringResource(R.string.add_members_confirm, state.totalToAdd))
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
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.add_members_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.typed.isNotEmpty()) {
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.typed.forEach { number ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removeTypedNumber(number) },
                            label = { Text(number) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            },
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text(stringResource(R.string.add_members_type_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitManual() }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { commitManual() },
                    enabled = manual.count { it.isDigit() } >= 4,
                    modifier = Modifier.focusHighlight(makeFocusable = true),
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }

            if (state.encrypted) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .focusHighlight(makeFocusable = true)
                        .clickable { viewModel.setShareKey(!state.shareKey) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = state.shareKey, onCheckedChange = viewModel::setShareKey)
                    Text(
                        stringResource(R.string.enc_key_share_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            state.error?.let {
                Text(
                    it.toUserMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when {
                state.loading -> LoadingBox()
                state.needsContactsPermission -> Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.add_members_needs_contacts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_allow),
                        onClick = { permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.filteredContacts, key = { it.e164 }) { contact ->
                        val isMember = contact.e164 in state.existingMembers
                        PickerRow(
                            title = contact.name,
                            subtitle = when {
                                isMember -> stringResource(R.string.add_members_already_member)
                                contact.isRegistered -> stringResource(R.string.contacts_on_tzibbur)
                                else -> contact.e164
                            },
                            checked = isMember || contact.e164 in state.selected,
                            enabled = !isMember,
                            onToggle = { viewModel.toggle(contact.e164) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusHighlight(makeFocusable = true)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { if (enabled) onToggle() }, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
