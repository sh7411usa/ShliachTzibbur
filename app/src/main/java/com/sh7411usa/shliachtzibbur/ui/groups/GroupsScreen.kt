package com.sh7411usa.shliachtzibbur.ui.groups

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.ui.AppViewModelFactory
import com.sh7411usa.shliachtzibbur.ui.common.EmptyState
import com.sh7411usa.shliachtzibbur.ui.common.ErrorRow
import com.sh7411usa.shliachtzibbur.ui.common.PrimaryButton
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    viewModel: GroupsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onCreateGroup) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.groups_new))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateGroup) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.groups_new))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (groups.isEmpty() && !state.refreshing) {
                EmptyState(
                    title = stringResource(R.string.groups_empty_title),
                    body = stringResource(R.string.groups_empty_body),
                    action = {
                        PrimaryButton(
                            text = stringResource(R.string.group_create_submit),
                            onClick = onCreateGroup,
                        )
                    },
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    state.error?.let { error ->
                        item {
                            ErrorRow(message = error.toUserMessage(), onRetry = viewModel::refresh)
                        }
                    }
                    items(groups, key = { it.id }) { group ->
                        GroupRow(group = group, onClick = { onOpenGroup(group.id) })
                        ThinDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(group: Group, onClick: () -> Unit) {
    val name = if (group.isSystem) stringResource(R.string.group_system_name) else group.name
    val subtitle = group.lastMessagePreview ?: categoryLabel(group.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(makeFocusable = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (group.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (group.muted) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.groups_muted),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (group.unreadCount > 0) {
            Text(
                text = stringResource(R.string.groups_unread_count, group.unreadCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
