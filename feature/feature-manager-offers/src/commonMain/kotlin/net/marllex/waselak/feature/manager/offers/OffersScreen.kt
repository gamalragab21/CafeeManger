package net.marllex.waselak.feature.manager.offers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import net.marllex.waselak.core.ui.platform.rememberImagePickerLauncher
import net.marllex.waselak.feature.manager.offers.generated.resources.Res
import net.marllex.waselak.feature.manager.offers.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersScreen(
    viewModel: OffersViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Delete confirmation dialog
    uiState.showDeleteConfirm?.let { offer ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text(stringResource(Res.string.delete_offer)) },
            text = { Text(stringResource(Res.string.delete_offer_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.showForm) {
                FloatingActionButton(
                    onClick = { viewModel.showAddForm() },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.add_offer))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.offers.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.offers.isEmpty() && !uiState.showForm) {
                // Empty state
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.no_offers),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.no_offers_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!uiState.showForm) {
                // Offers list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.offers, key = { it.id }) { offer ->
                        OfferCard(
                            offer = offer,
                            onEdit = { viewModel.showEditForm(offer) },
                            onDelete = { viewModel.showDeleteConfirm(offer) },
                            onToggle = { viewModel.toggleOffer(offer) },
                        )
                    }
                }
            }

            // Form overlay
            if (uiState.showForm) {
                OfferFormContent(
                    uiState = uiState,
                    onDismiss = { viewModel.dismissForm() },
                    onSave = { viewModel.saveOffer() },
                    onNameChange = viewModel::updateFormName,
                    onDescriptionChange = viewModel::updateFormDescription,
                    onUploadImage = viewModel::uploadOfferImage,
                    onRemoveImage = viewModel::removeOfferImage,
                    onDiscountTypeChange = viewModel::updateFormDiscountType,
                    onDiscountValueChange = viewModel::updateFormDiscountValue,
                    onActiveChange = viewModel::updateFormActive,
                    onExpiresAtChange = viewModel::updateFormExpiresAt,
                    onPromoCodeChange = viewModel::updateFormPromoCode,
                    onMaxUsesChange = viewModel::updateFormMaxUses,
                    onStartsAtChange = viewModel::updateFormStartsAt,
                    onAddItem = viewModel::addItemToOffer,
                    onRemoveItem = viewModel::removeItemFromOffer,
                    onUpdateQuantity = viewModel::updateItemQuantity,
                    onSearchChange = viewModel::updateItemSearchQuery,
                )
            }
        }
    }
}

@Composable
private fun OfferCard(
    offer: Offer,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
) {
    val now = Clock.System.now().toEpochMilliseconds()
    val expiresAt = offer.expiresAt
    val isExpired = expiresAt != null && expiresAt < now

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Header with image or gradient
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
            ) {
                if (!offer.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = offer.imageUrl,
                        contentDescription = offer.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Gradient placeholder
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                )
                            )
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = offer.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Badges
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isExpired) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(stringResource(Res.string.expired), modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    Badge(
                        containerColor = if (offer.active && !isExpired) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = if (offer.active) stringResource(Res.string.active) else stringResource(Res.string.inactive),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                // Discount badge
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (offer.discountType == "PERCENT") {
                            "${offer.discountValue.toInt()}% ${stringResource(Res.string.off)}"
                        } else {
                            "${offer.discountValue} ${stringResource(Res.string.fixed_price)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Info section
            Column(modifier = Modifier.padding(12.dp)) {
                val desc = offer.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Promo code badge
                val promoCode = offer.promoCode
                if (!promoCode.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                text = promoCode,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }

                // Usage stats
                val maxUses = offer.maxUses
                if (maxUses != null) {
                    Text(
                        text = stringResource(Res.string.usage_stats, offer.usedCount, maxUses),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (offer.usedCount > 0) {
                    Text(
                        text = stringResource(Res.string.usage_unlimited, offer.usedCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Items count
                Text(
                    text = "${offer.items.size} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (expiresAt != null) {
                    val instant = Instant.fromEpochMilliseconds(expiresAt)
                    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                    Text(
                        text = "Expires: ${dateTime.date}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExpired) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = offer.active,
                        onCheckedChange = { onToggle() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(Res.string.edit_offer),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.delete_offer),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferFormContent(
    uiState: OffersViewModel.UiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onUploadImage: (ByteArray) -> Unit,
    onRemoveImage: () -> Unit,
    onDiscountTypeChange: (String) -> Unit,
    onDiscountValueChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onExpiresAtChange: (Long?) -> Unit,
    onPromoCodeChange: (String) -> Unit,
    onMaxUsesChange: (String) -> Unit,
    onStartsAtChange: (Long?) -> Unit,
    onAddItem: (net.marllex.waselak.core.model.Item) -> Unit,
    onRemoveItem: (String) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    val isEditing = uiState.editingOffer != null
    val canSave = uiState.formName.isNotBlank() &&
            uiState.formDiscountValue.toDoubleOrNull() != null &&
            uiState.formDiscountValue.toDoubleOrNull()!! > 0 &&
            uiState.formSelectedItems.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    if (isEditing) stringResource(Res.string.edit_offer)
                    else stringResource(Res.string.add_offer)
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel))
                }
            },
            actions = {
                TextButton(
                    onClick = onSave,
                    enabled = canSave && !uiState.isSaving,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.save))
                    }
                }
            }
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Name
            OutlinedTextField(
                value = uiState.formName,
                onValueChange = onNameChange,
                label = { Text(stringResource(Res.string.offer_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Description
            OutlinedTextField(
                value = uiState.formDescription,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(Res.string.offer_description)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            // Image picker
            val pickImage = rememberImagePickerLauncher { bytes ->
                if (bytes != null) onUploadImage(bytes)
            }

            if (uiState.formImageUrl.isNotBlank()) {
                // Show image preview with remove button
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { pickImage() },
                ) {
                    AsyncImage(
                        model = uiState.formImageUrl,
                        contentDescription = stringResource(Res.string.offer_change_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Remove button
                    Surface(
                        onClick = onRemoveImage,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp).align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.delete),
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onError,
                        )
                    }
                    // Change image label
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.offer_change_image),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            } else if (uiState.isUploadingImage) {
                // Uploading state
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(Res.string.offer_uploading_image),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                // Empty state - dashed border placeholder
                OutlinedCard(
                    onClick = { pickImage() },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = stringResource(Res.string.offer_add_image),
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.offer_add_image),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Discount type selector
            Text(
                text = stringResource(Res.string.discount_type),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.formDiscountType == "FIXED_PRICE",
                    onClick = { onDiscountTypeChange("FIXED_PRICE") },
                    label = { Text(stringResource(Res.string.fixed_price)) },
                )
                FilterChip(
                    selected = uiState.formDiscountType == "PERCENT",
                    onClick = { onDiscountTypeChange("PERCENT") },
                    label = { Text(stringResource(Res.string.percentage)) },
                )
            }

            // Discount value
            OutlinedTextField(
                value = uiState.formDiscountValue,
                onValueChange = onDiscountValueChange,
                label = {
                    Text(stringResource(Res.string.discount_value) +
                        if (uiState.formDiscountType == "PERCENT") " (%)" else "")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            // Active toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (uiState.formActive) stringResource(Res.string.active) else stringResource(Res.string.inactive),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.formActive,
                    onCheckedChange = onActiveChange,
                )
            }

            // Promo Code
            OutlinedTextField(
                value = uiState.formPromoCode,
                onValueChange = onPromoCodeChange,
                label = { Text(stringResource(Res.string.promo_code)) },
                placeholder = { Text(stringResource(Res.string.promo_code_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Max Uses
            OutlinedTextField(
                value = uiState.formMaxUses,
                onValueChange = onMaxUsesChange,
                label = { Text(stringResource(Res.string.max_uses)) },
                placeholder = { Text(stringResource(Res.string.max_uses_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            HorizontalDivider()

            // Selected items section
            Text(
                text = stringResource(Res.string.select_items),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // Selected items list
            uiState.formSelectedItems.forEach { selected ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selected.itemName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${selected.itemPrice}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Quantity controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = { onUpdateQuantity(selected.itemId, selected.quantity - 1) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${selected.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(
                                onClick = { onUpdateQuantity(selected.itemId, selected.quantity + 1) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onRemoveItem(selected.itemId) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            // Search and add items
            OutlinedTextField(
                value = uiState.itemSearchQuery,
                onValueChange = onSearchChange,
                label = { Text(stringResource(Res.string.search_items)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Available items
            val selectedIds = uiState.formSelectedItems.map { it.itemId }.toSet()
            val filteredItems = uiState.allItems
                .filter { it.id !in selectedIds }
                .filter {
                    uiState.itemSearchQuery.isBlank() ||
                        it.name.contains(uiState.itemSearchQuery, ignoreCase = true)
                }

            if (filteredItems.isEmpty() && uiState.allItems.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.no_items_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }

            filteredItems.take(20).forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onAddItem(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${item.price}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Bottom spacer for FAB
            Spacer(Modifier.height(80.dp))
        }
    }
}

// Need this import for the OfferCard to reference the model
private typealias Offer = net.marllex.waselak.core.model.Offer
