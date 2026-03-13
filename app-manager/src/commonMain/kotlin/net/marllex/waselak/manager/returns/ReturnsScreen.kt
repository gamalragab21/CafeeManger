package net.marllex.waselak.manager.returns

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
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.ProductReturn
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnsScreen(
    viewModel: ReturnsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val statusFilters = listOf(null to stringResource(Res.string.all), "PENDING" to stringResource(Res.string.pending), "COMPLETED" to stringResource(Res.string.completed), "REJECTED" to stringResource(Res.string.rejected))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.returns_exchanges)) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryChip(stringResource(Res.string.total_label, uiState.summary.total.toString()), Modifier.weight(1f))
                SummaryChip(stringResource(Res.string.pending_label, uiState.summary.pending.toString()), Modifier.weight(1f))
                SummaryChip(stringResource(Res.string.refunded_label, uiState.summary.totalRefunded.toString()), Modifier.weight(1f))
            }

            // Status filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                statusFilters.forEach { (status, label) ->
                    FilterChip(
                        selected = uiState.selectedStatus == status,
                        onClick = { viewModel.onStatusFilter(status) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null && uiState.returns.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                uiState.filteredReturns.isEmpty() -> EmptyView(stringResource(Res.string.no_returns_found))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(uiState.filteredReturns, key = { it.id }) { ret ->
                        ReturnCard(ret = ret, onApprove = { viewModel.processReturn(ret.id, "COMPLETED") }, onReject = { viewModel.processReturn(ret.id, "REJECTED") })
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ReturnCard(ret: ProductReturn, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(ret.returnType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SuggestionChip(onClick = {}, label = { Text(ret.status) })
            }
            Text(stringResource(Res.string.reason_prefix, ret.reason), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.return_items_refund, ret.items.size, ret.refundAmount.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ret.isPending) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.approve)) }
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.reject)) }
                }
            }
        }
    }
}
