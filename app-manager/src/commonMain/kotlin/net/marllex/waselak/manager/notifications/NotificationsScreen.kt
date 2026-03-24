package net.marllex.waselak.manager.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.AppNotification
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Auto-refresh notifications every 15 seconds while screen is visible
    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.notifications),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (uiState.count.hasUnread) {
                        TextButton(onClick = viewModel::markAllRead) { Text(stringResource(Res.string.mark_all_read)) }
                    }
                    IconButton(onClick = viewModel::toggleUnreadFilter) {
                        Icon(
                            if (uiState.showUnreadOnly) Icons.Default.FilterList else Icons.Default.FilterListOff,
                            contentDescription = stringResource(Res.string.filter),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.notifications.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
            uiState.displayedNotifications.isEmpty() -> EmptyView(if (uiState.showUnreadOnly) stringResource(Res.string.no_unread_notifications) else stringResource(Res.string.no_notifications_yet))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.displayedNotifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onMarkRead = { viewModel.markRead(notification.id) },
                        onDelete = { viewModel.delete(notification.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(notification: AppNotification, onMarkRead: () -> Unit, onDelete: () -> Unit) {
    val bgAlpha = if (notification.isUnread) 1f else 0.6f
    Card(
        onClick = { if (notification.isUnread) onMarkRead() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (notification.isUnread) {
                        Badge(modifier = Modifier.size(8.dp)) {}
                    }
                    Text(notification.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (notification.isUnread) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(Modifier.height(4.dp))
                Text(notification.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notification.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(notification.priority, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.delete), modifier = Modifier.size(18.dp))
            }
        }
    }
}
