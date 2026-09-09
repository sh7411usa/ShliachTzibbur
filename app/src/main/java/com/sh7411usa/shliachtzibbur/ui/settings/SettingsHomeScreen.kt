package com.sh7411usa.shliachtzibbur.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.model.LegalKind
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onOpenAccount: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLegal: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_settings_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Entry(stringResource(R.string.user_settings_title), onOpenAccount)
            ThinDivider()
            Entry(stringResource(R.string.app_settings_section_appearance) + " · " + stringResource(R.string.app_settings_section_notifications), onOpenAppSettings)
            ThinDivider()
            Entry(stringResource(R.string.user_settings_privacy)) { onOpenLegal(LegalKind.PRIVACY.slug) }
            Entry(stringResource(R.string.user_settings_terms)) { onOpenLegal(LegalKind.TERMS.slug) }
        }
    }
}

@Composable
private fun Entry(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(makeFocusable = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}
