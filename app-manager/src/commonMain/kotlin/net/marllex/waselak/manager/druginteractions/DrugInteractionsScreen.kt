package net.marllex.waselak.manager.druginteractions

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.DrugInteraction
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugInteractionsScreen(
    viewModel: DrugInteractionsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.drug_interactions)) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddDialog, containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_interaction))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(Res.string.search_interactions)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null && uiState.interactions.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                uiState.filteredInteractions.isEmpty() -> EmptyView(stringResource(Res.string.no_drug_interactions))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(uiState.filteredInteractions, key = { it.id }) { interaction ->
                        DrugInteractionCard(interaction = interaction, onToggle = { viewModel.toggle(interaction.id) }, onDelete = { viewModel.delete(interaction.id) })
                    }
                }
            }
        }
    }

    // Add Dialog
    if (uiState.showAddDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAddDialog,
            title = { Text(stringResource(Res.string.add_drug_interaction)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.dialogItemIdA, onValueChange = viewModel::onDialogItemIdAChange, label = { Text("${stringResource(Res.string.item_a_id)} *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.dialogItemIdB, onValueChange = viewModel::onDialogItemIdBChange, label = { Text("${stringResource(Res.string.item_b_id)} *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = uiState.dialogDescription, onValueChange = viewModel::onDialogDescriptionChange, label = { Text("${stringResource(Res.string.description)} *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uiState.dialogRecommendation, onValueChange = viewModel::onDialogRecommendationChange, label = { Text(stringResource(Res.string.recommendation)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = viewModel::save, enabled = !uiState.isSaving) {
                    if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(stringResource(Res.string.add))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissAddDialog) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun DrugInteractionCard(interaction: DrugInteraction, onToggle: () -> Unit, onDelete: () -> Unit) {
    val severityColor = when (interaction.severity) {
        "MILD" -> Color(0xFF4CAF50)
        "MODERATE" -> Color(0xFFFFC107)
        "SEVERE" -> Color(0xFFFF9800)
        "CONTRAINDICATED" -> Color(0xFFF44336)
        else -> Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(interaction.itemNameA ?: interaction.itemIdA, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(interaction.itemNameB ?: interaction.itemIdB, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                SuggestionChip(onClick = {}, label = { Text(interaction.severity, color = severityColor) })
            }
            Spacer(Modifier.height(4.dp))
            Text(interaction.description, style = MaterialTheme.typography.bodyMedium)
            interaction.recommendation?.let { Text(stringResource(Res.string.recommendation_prefix, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggle) { Text(if (interaction.active) stringResource(Res.string.disable) else stringResource(Res.string.enable)) }
                TextButton(onClick = onDelete) { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
