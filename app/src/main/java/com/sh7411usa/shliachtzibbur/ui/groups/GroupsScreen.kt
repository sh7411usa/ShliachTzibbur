package com.sh7411usa.shliachtzibbur.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.sh7411usa.shliachtzibbur.ui.common.SectionHeader
import com.sh7411usa.shliachtzibbur.ui.common.ThinDivider
import com.sh7411usa.shliachtzibbur.ui.common.focusHighlight
import com.sh7411usa.shliachtzibbur.ui.common.toUserMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: GroupsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(R.string.search_groups_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.groups_title))
                    }
                },
                navigationIcon = {
                    if (searchOpen) {
                        IconButton(onClick = { searchOpen = false; viewModel.setQuery("") }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.setQuery("")
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_groups_hint))
                    }
                    if (!searchOpen) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                        IconButton(onClick = onCreateGroup) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.groups_new))
                        }
                        IconButton(onClick = onOpenContacts) {
                            Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_contacts))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                        }
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
        if (searchOpen && query.isNotBlank()) {
            SearchResultsList(
                results = searchResults,
                onOpenGroup = onOpenGroup,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
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
private fun SearchResultsList(
    results: SearchResults,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.groups.isEmpty() && results.messages.isEmpty()) {
        EmptyState(title = stringResource(R.string.search_no_results), modifier = modifier)
        return
    }
    LazyColumn(modifier) {
        if (results.groups.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.nav_groups)) }
            items(results.groups, key = { "g-${it.id}" }) { group ->
                GroupRow(group = group, onClick = { onOpenGroup(group.id) })
                ThinDivider(Modifier.padding(start = 16.dp))
            }
        }
        if (results.messages.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.search_section_messages)) }
            items(results.messages, key = { "m-${it.message.id}" }) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .focusHighlight(makeFocusable = true)
                        .clickable { onOpenGroup(hit.groupId) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(hit.groupName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        hit.message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ThinDivider(Modifier.padding(start = 16.dp))
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
            UnreadBadge(group.unreadCount)
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
