package net.marllex.waselak.cashier.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierNotificationsScreen(
    viewModel: CashierNotificationsViewModel = koinViewModel(),
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
                        TextButton(onClick = viewModel::markAllRead) { Text(stringResource(Res.string.read_all)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                uiState.notifications.isEmpty() -> EmptyView(stringResource(Res.string.no_notifications))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(uiState.notifications, key = { it.id }) { n ->
                        CashierNotificationCard(n, onRead = { viewModel.markRead(n.id) }, onDelete = { viewModel.delete(n.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashierNotificationCard(notification: AppNotification, onRead: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = { if (notification.isUnread) onRead() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (notification.isUnread) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (notification.isUnread) Badge(modifier = Modifier.size(8.dp)) {}
                    Text(notification.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (notification.isUnread) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(Modifier.height(4.dp))
                Text(notification.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(notification.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.delete), modifier = Modifier.size(18.dp)) }
        }
    }
}
