package net.marllex.waselak.manager.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.database.Pending_attendance
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*

@Composable
fun OfflineSettingsScreen(
    viewModel: OfflineSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null && !uiState.enableOfflineMode -> ErrorView(
            message = uiState.error!!,
            onRetry = viewModel::load,
        )
        else -> LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Global Toggle Section
            item {
                Text(
                    text = stringResource(CoreRes.string.offline_mode_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(CoreRes.string.enable_offline_mode),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(CoreRes.string.enable_offline_mode_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.enableOfflineMode,
                            onCheckedChange = viewModel::toggleOfflineMode,
                            enabled = !uiState.isSaving,
                        )
                    }
                }
            }

            // Sync Dashboard Section
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(CoreRes.string.sync_dashboard),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Pending Records Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(CoreRes.string.pending_records),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${uiState.pendingRecords.size}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(CoreRes.string.failed_records),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${uiState.failedRecords.size}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.failedRecords.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // Pending Records List
            if (uiState.pendingRecords.isEmpty() && uiState.failedRecords.isEmpty()) {
                item {
                    Text(
                        text = stringResource(CoreRes.string.no_pending_records),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            // Failed Records with actions
            if (uiState.failedRecords.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(CoreRes.string.failed_records),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(uiState.failedRecords, key = { it.id }) { record ->
                    PendingRecordCard(
                        record = record,
                        isFailed = true,
                        onRetry = { viewModel.retryFailed(record.id) },
                        onDelete = { viewModel.deleteFailed(record.id) },
                    )
                }
            }

            // Pending (non-failed) Records
            val activePending = uiState.pendingRecords.filter { it.retry_count < 3 }
            if (activePending.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(CoreRes.string.pending_records),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(activePending, key = { it.id }) { record ->
                    PendingRecordCard(
                        record = record,
                        isFailed = false,
                        onRetry = null,
                        onDelete = null,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PendingRecordCard(
    record: Pending_attendance,
    isFailed: Boolean,
    onRetry: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isFailed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(
                    text = record.worker_name ?: record.worker_id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${record.action} - ${record.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRetry != null) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(CoreRes.string.retry_sync),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(CoreRes.string.delete_failed),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
