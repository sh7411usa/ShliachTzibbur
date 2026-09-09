package com.sh7411usa.shliachtzibbur.ui.auth

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.util.DeviceInfo
import com.sh7411usa.shliachtzibbur.core.util.PhoneDetection
import com.sh7411usa.shliachtzibbur.core.util.SimNumbers
import com.sh7411usa.shliachtzibbur.core.util.SimOption
import com.sh7411usa.shliachtzibbur.ui.common.PrimaryButton
import com.sh7411usa.shliachtzibbur.ui.common.SecondaryButton
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage

@Composable
fun AuthLandingScreen(onContinueWithPhone: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                stringResource(R.string.auth_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.auth_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(40.dp))
            PrimaryButton(
                text = stringResource(R.string.auth_continue_with_phone),
                onClick = onContinueWithPhone,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.auth_email_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.auth_google_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneEntryScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onDetectPhone: (PhoneDetection) -> Unit,
    onChooseSim: (SimOption?) -> Unit,
    onSubmit: (phoneRaw: String, region: String?, nickname: String?) -> Unit,
) {
    val context = LocalContext.current
    var phone by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(DeviceInfo.defaultRegion(context) ?: "") }
    var nickname by rememberSaveable { mutableStateOf("") }

    // Adopt a prefill (last number or a SIM number) only if the user hasn't typed.
    LaunchedEffect(state.phonePrefill) {
        if (state.phonePrefill.isNotBlank() && phone.isBlank()) phone = state.phonePrefill
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onDetectPhone(SimNumbers.detect(context)) }

    LaunchedEffect(Unit) {
        if (state.phonePrefill.isBlank() && state.simChoices == null && !state.needsPhonePermission) {
            onDetectPhone(SimNumbers.detect(context))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_phone_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.auth_phone_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            state.simChoices?.let { choices ->
                Text(stringResource(R.string.auth_choose_number), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                    choices.forEach { option ->
                        SimChoiceRow(option.label + " · " + option.e164) { onChooseSim(option) }
                    }
                    SimChoiceRow(stringResource(R.string.auth_other_number)) { onChooseSim(null) }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.needsPhonePermission) {
                Text(
                    stringResource(R.string.phone_permission_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryButton(
                    text = stringResource(R.string.action_allow),
                    onClick = { permissionLauncher.launch(SimNumbers.requiredPermissions) },
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                label = { Text(stringResource(R.string.auth_phone_e164_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = region,
                onValueChange = { region = it.take(2).uppercase() },
                label = { Text(stringResource(R.string.auth_region_hint)) },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )

            if (state.needsNickname) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.auth_nickname_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(64) },
                    label = { Text(stringResource(R.string.auth_nickname_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { error ->
                Text(
                    error.toUserMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = stringResource(
                    if (state.needsNickname) R.string.action_next else R.string.auth_send_code,
                ),
                onClick = {
                    onSubmit(phone, region, nickname.takeIf { state.needsNickname })
                },
                enabled = phone.count { it.isDigit() } >= 8 &&
                    (!state.needsNickname || nickname.isNotBlank()),
                loading = state.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeVerifyScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onStartAutoDetect: () -> Unit,
    onStopAutoDetect: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.detectedCode) {
        state.detectedCode?.let {
            code = it
            if (it.length == 6) onVerify(it)
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onStartAutoDetect() }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) onStartAutoDetect() else smsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_code_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.phoneE164?.let {
                Text(
                    stringResource(R.string.auth_code_sent_to, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            if (state.autoDetecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.auth_waiting_for_sms),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onStopAutoDetect, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.auth_enter_manually))
                }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.auth_code_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { error ->
                    Text(
                        error.toUserMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton(
                    text = stringResource(R.string.auth_verify),
                    onClick = { onVerify(code) },
                    enabled = code.length == 6,
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (state.resendInSeconds > 0) {
                    Text(
                        stringResource(R.string.auth_resend_in, state.resendInSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = onResend) { Text(stringResource(R.string.auth_resend)) }
                }
            }
        }
    }
}

@Composable
private fun SimChoiceRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = false, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = false, onClick = null)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
}
