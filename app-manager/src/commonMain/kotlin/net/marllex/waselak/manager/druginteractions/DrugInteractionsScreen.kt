package net.marllex.waselak.manager.druginteractions

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.DrugInteraction
import net.marllex.waselak.core.model.Item
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
            WaselakTopAppBar(
                title = stringResource(Res.string.drug_interactions),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = onNavigateBack,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddDialog, containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_interaction))
            }
        }
    ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
        AddDrugInteractionDialog(
            uiState = uiState,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDrugInteractionDialog(
    uiState: DrugInteractionsViewModel.UiState,
    viewModel: DrugInteractionsViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::dismissAddDialog,
        title = { Text(stringResource(Res.string.add_drug_interaction)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Item A picker
                Text(stringResource(Res.string.item_a_id), style = MaterialTheme.typography.labelMedium)
                ItemPickerField(
                    selectedItem = uiState.dialogItemA,
                    searchQuery = uiState.dialogSearchA,
                    onSearchChange = viewModel::onDialogSearchAChange,
                    filteredItems = uiState.filteredItemsA,
                    onSelect = viewModel::selectItemA,
                    onClear = viewModel::clearItemA,
                )

                // Item B picker
                Text(stringResource(Res.string.item_b_id), style = MaterialTheme.typography.labelMedium)
                ItemPickerField(
                    selectedItem = uiState.dialogItemB,
                    searchQuery = uiState.dialogSearchB,
                    onSearchChange = viewModel::onDialogSearchBChange,
                    filteredItems = uiState.filteredItemsB,
                    onSelect = viewModel::selectItemB,
                    onClear = viewModel::clearItemB,
                )

                // Severity dropdown
                Text(stringResource(Res.string.severity), style = MaterialTheme.typography.labelMedium)
                SeverityDropdown(
                    selected = uiState.dialogSeverity,
                    onSelect = viewModel::onDialogSeverityChange,
                )

                // Description
                OutlinedTextField(
                    value = uiState.dialogDescription,
                    onValueChange = viewModel::onDialogDescriptionChange,
                    label = { Text("${stringResource(Res.string.description)} *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                // Description Arabic
                OutlinedTextField(
                    value = uiState.dialogDescriptionAr,
                    onValueChange = viewModel::onDialogDescriptionArChange,
                    label = { Text(stringResource(Res.string.description_ar)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                // Recommendation
                OutlinedTextField(
                    value = uiState.dialogRecommendation,
                    onValueChange = viewModel::onDialogRecommendationChange,
                    label = { Text(stringResource(Res.string.recommendation)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::save,
                enabled = !uiState.isSaving && uiState.dialogItemIdA.isNotBlank() && uiState.dialogItemIdB.isNotBlank() && uiState.dialogDescription.isNotBlank(),
            ) {
                if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.add))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissAddDialog) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun ItemPickerField(
    selectedItem: Item?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filteredItems: List<Item>,
    onSelect: (Item) -> Unit,
    onClear: () -> Unit,
) {
    if (selectedItem != null) {
        // Show selected item with clear button
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(selectedItem.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    selectedItem.barcode?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    } else {
        // Show search field with dropdown
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    onSearchChange(it)
                    expanded = it.isNotBlank()
                },
                placeholder = { Text(stringResource(Res.string.search_item)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(8.dp),
            )
            DropdownMenu(
                expanded = expanded && filteredItems.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 200.dp),
            ) {
                filteredItems.take(10).forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                item.barcode?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeverityDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val severities = listOf(
        "MILD" to stringResource(Res.string.severity_mild),
        "MODERATE" to stringResource(Res.string.severity_moderate),
        "SEVERE" to stringResource(Res.string.severity_severe),
        "CONTRAINDICATED" to stringResource(Res.string.severity_contraindicated),
    )
    val selectedLabel = severities.firstOrNull { it.first == selected }?.second ?: selected
    val selectedColor = severityColor(selected)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = selectedColor,
                unfocusedTextColor = selectedColor,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            severities.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = severityColor(key)) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun severityColor(severity: String): Color = when (severity) {
    "MILD" -> Color(0xFF4CAF50)
    "MODERATE" -> Color(0xFFFFC107)
    "SEVERE" -> Color(0xFFFF9800)
    "CONTRAINDICATED" -> Color(0xFFF44336)
    else -> Color.Gray
}

@Composable
private fun DrugInteractionCard(interaction: DrugInteraction, onToggle: () -> Unit, onDelete: () -> Unit) {
    val sColor = severityColor(interaction.severity)
    val severityLabel = when (interaction.severity) {
        "MILD" -> stringResource(Res.string.severity_mild)
        "MODERATE" -> stringResource(Res.string.severity_moderate)
        "SEVERE" -> stringResource(Res.string.severity_severe)
        "CONTRAINDICATED" -> stringResource(Res.string.severity_contraindicated)
        else -> interaction.severity
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!interaction.active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(interaction.itemNameA ?: interaction.itemIdA, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(interaction.itemNameB ?: interaction.itemIdB, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                SuggestionChip(onClick = {}, label = { Text(severityLabel, color = sColor, style = MaterialTheme.typography.labelSmall) })
            }
            Spacer(Modifier.height(4.dp))
            Text(interaction.description, style = MaterialTheme.typography.bodyMedium)
            interaction.recommendation?.let {
                Text(
                    stringResource(Res.string.recommendation_prefix, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!interaction.active) {
                Text(
                    stringResource(Res.string.interaction_inactive),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggle) {
                    Text(if (interaction.active) stringResource(Res.string.disable) else stringResource(Res.string.enable))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
