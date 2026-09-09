package com.sh7411usa.shliachtzibbur.ui.appsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.BuildConfig
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.model.ThemeMode
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.SectionHeader
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider
import com.sh7411usa.shliachtzibbur.ui.locale.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    viewModel: AppSettingsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
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
            SectionHeader(stringResource(R.string.app_settings_theme))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.app_settings_theme_system
                                ThemeMode.LIGHT -> R.string.app_settings_theme_light
                                ThemeMode.DARK -> R.string.app_settings_theme_dark
                            },
                        ),
                        selected = settings.themeMode == mode,
                        onSelect = { viewModel.setTheme(mode) },
                    )
                }
            }

            ThinDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.app_settings_language))
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    ChoiceRow(
                        label = if (language == AppLanguage.SYSTEM) {
                            stringResource(R.string.app_settings_language_system)
                        } else {
                            language.endonym
                        },
                        selected = settings.languageTag == language.tag,
                        onSelect = { viewModel.setLanguage(language.tag) },
                    )
                }
            }

            ThinDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.app_settings_section_notifications))
            SwitchRow(
                label = stringResource(R.string.app_settings_notifications_enabled),
                checked = settings.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled,
            )
            SwitchRow(
                label = stringResource(R.string.app_settings_sync_service),
                description = stringResource(R.string.app_settings_sync_service_explain),
                checked = settings.syncServiceEnabled,
                onCheckedChange = viewModel::setSyncServiceEnabled,
            )

            ThinDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.app_settings_section_about))
            Text(
                stringResource(R.string.app_settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Text(
                stringResource(R.string.app_settings_open_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.height(0.dp))
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
