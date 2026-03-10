package net.marllex.waselak.manager.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoyaltyDiscountsScreen(
    viewModel: RestaurantProfileViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Settings saved successfully")
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreRes.string.loyalty_and_discounts)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.vendor == null -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadVendor,
            )

            else -> {
                val vendor = uiState.vendor
                if (vendor != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // ── Header Banner ──
                        item {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF57C00).copy(alpha = 0.08f),
                                ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                Color(0xFFF57C00).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(14.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.CardGiftcard,
                                            contentDescription = null,
                                            tint = Color(0xFFF57C00),
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            stringResource(CoreRes.string.loyalty_and_discounts),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            stringResource(CoreRes.string.loyalty_discounts_screen_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // ── Loyalty Points Section ──
                        item {
                            LoyaltyPointsCard(
                                vendor = vendor,
                                isSaving = uiState.loyaltySaving,
                                onToggleLoyalty = viewModel::toggleLoyalty,
                                onSaveLoyaltySettings = viewModel::updateLoyaltySettings,
                            )
                        }

                        // ── Manual Discount Section ──
                        item {
                            ManualDiscountCard(
                                vendor = vendor,
                                isSaving = uiState.loyaltySaving,
                                onSaveDiscountSettings = viewModel::updateDiscountSettings,
                            )
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ─── Loyalty Points Card ──────────────────────────────────────────────
@Composable
private fun LoyaltyPointsCard(
    vendor: Vendor,
    isSaving: Boolean,
    onToggleLoyalty: (Boolean) -> Unit,
    onSaveLoyaltySettings: (Double?, Double?, Int?) -> Unit,
) {
    var editEarnRate by remember(vendor.pointsEarnRate) { mutableStateOf(vendor.pointsEarnRate.toString()) }
    var editRedeemRate by remember(vendor.pointsRedeemRate) { mutableStateOf(vendor.pointsRedeemRate.toString()) }
    var editMinRedeem by remember(vendor.minPointsRedeem) { mutableStateOf(vendor.minPointsRedeem.toString()) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            // Section Header with icon
            SectionHeader(
                icon = Icons.Filled.Stars,
                iconTint = Color(0xFF1565C0),
                title = stringResource(CoreRes.string.loyalty_points),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(CoreRes.string.loyalty_points_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Toggle
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(CoreRes.string.loyalty_points_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Switch(
                        checked = vendor.loyaltyEnabled,
                        onCheckedChange = onToggleLoyalty,
                    )
                }
            }

            // Settings (visible when enabled)
            if (vendor.loyaltyEnabled) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Points Earn Rate
                LoyaltySettingsField(
                    label = stringResource(CoreRes.string.points_earn_rate),
                    description = stringResource(CoreRes.string.points_earn_rate_desc),
                    value = editEarnRate,
                    onValueChange = { editEarnRate = it },
                )

                Spacer(Modifier.height(16.dp))

                // Points Redeem Rate
                LoyaltySettingsField(
                    label = stringResource(CoreRes.string.points_redeem_rate),
                    description = stringResource(CoreRes.string.points_redeem_rate_desc),
                    value = editRedeemRate,
                    onValueChange = { editRedeemRate = it },
                )

                Spacer(Modifier.height(16.dp))

                // Min Points to Redeem
                LoyaltySettingsField(
                    label = stringResource(CoreRes.string.min_points_redeem),
                    description = stringResource(CoreRes.string.min_points_redeem_desc),
                    value = editMinRedeem,
                    onValueChange = { editMinRedeem = it },
                    isDecimal = false,
                )

                Spacer(Modifier.height(16.dp))

                // Save Button
                Button(
                    onClick = {
                        onSaveLoyaltySettings(
                            editEarnRate.toDoubleOrNull(),
                            editRedeemRate.toDoubleOrNull(),
                            editMinRedeem.toIntOrNull(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving,
                ) {
                    Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(CoreRes.string.save_settings))
                }
            }
        }
    }
}

// ─── Manual Discount Card ──────────────────────────────────────────────
@Composable
private fun ManualDiscountCard(
    vendor: Vendor,
    isSaving: Boolean,
    onSaveDiscountSettings: (Double?, Boolean?) -> Unit,
) {
    var editMaxDiscount by remember(vendor.maxManualDiscountPercent) {
        mutableStateOf(vendor.maxManualDiscountPercent.toString())
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            // Section Header with icon
            SectionHeader(
                icon = Icons.Filled.Discount,
                iconTint = Color(0xFF2E7D32),
                title = stringResource(CoreRes.string.manual_discount),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(CoreRes.string.manual_discount_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Max Discount Percent
            LoyaltySettingsField(
                label = stringResource(CoreRes.string.max_discount_percent),
                description = stringResource(CoreRes.string.max_discount_percent_desc),
                value = editMaxDiscount,
                onValueChange = { editMaxDiscount = it },
            )

            Spacer(Modifier.height(16.dp))

            // Require Manager PIN toggle
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(CoreRes.string.require_manager_pin),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(CoreRes.string.require_manager_pin_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = vendor.manualDiscountRequiresPin,
                        onCheckedChange = { newVal ->
                            onSaveDiscountSettings(null, newVal)
                        },
                        enabled = !isSaving,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save Discount Settings Button
            Button(
                onClick = {
                    onSaveDiscountSettings(editMaxDiscount.toDoubleOrNull(), null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving,
            ) {
                Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CoreRes.string.save_settings))
            }
        }
    }
}

// ─── Reusable Components ──────────────────────────────────────────────

@Composable
private fun SectionHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconTint.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LoyaltySettingsField(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    isDecimal: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
        )
    }
}
