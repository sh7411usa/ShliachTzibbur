package com.sh7411usa.shliachtzibbur.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.util.DeviceInfo
import com.sh7411usa.shliachtzibbur.ui.common.PrimaryButton
import com.sh7411usa.shliachtzibbur.ui.common.SecondaryButton
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage
import androidx.compose.ui.platform.LocalContext

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
    onSubmit: (callingCode: String, national: String, displayName: String, region: String) -> Unit,
) {
    val context = LocalContext.current
    var callingCode by rememberSaveable { mutableStateOf("+1") }
    var national by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(DeviceInfo.defaultRegion(context) ?: "") }

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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = callingCode,
                    onValueChange = { callingCode = it.take(5) },
                    label = { Text(stringResource(R.string.auth_country_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = national,
                    onValueChange = { national = it },
                    label = { Text(stringResource(R.string.auth_phone_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(64) },
                label = { Text(stringResource(R.string.auth_display_name)) },
                supportingText = { Text(stringResource(R.string.auth_display_name_explain)) },
                singleLine = true,
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
                text = stringResource(R.string.auth_send_code),
                onClick = { onSubmit(callingCode, national, displayName, region) },
                enabled = national.isNotBlank(),
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
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }

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

@Composable
private fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
}
