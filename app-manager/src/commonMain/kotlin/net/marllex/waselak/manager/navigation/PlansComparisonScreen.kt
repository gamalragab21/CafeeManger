package net.marllex.waselak.manager.navigation

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.network.dto.PlanFeaturesDto
import net.marllex.waselak.core.network.dto.PlanLimitsDto
import net.marllex.waselak.core.network.dto.PlanSummaryDto
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*

@Composable
fun PlansComparisonScreen(
    viewModel: PlansComparisonViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorView(
            message = uiState.error!!,
            onRetry = viewModel::retry,
        )
        else -> {
            val currentPlanName = uiState.currentPlan?.planName
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.allPlans) { plan ->
                    PlanCard(
                        plan = plan,
                        isCurrentPlan = plan.name == currentPlanName,
                    )
                }

                item {
                    Text(
                        text = stringResource(CoreRes.string.contact_admin_upgrade),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanSummaryDto,
    isCurrentPlan: Boolean,
) {
    val badgeColor = when (plan.name.uppercase()) {
        "ENTERPRISE" -> MaterialTheme.colorScheme.tertiary
        "BUSINESS" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentPlan) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentPlan)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            // Header: Plan name + price badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = plan.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeColor,
                ) {
                    Text(
                        text = stringResource(CoreRes.string.egp_month, plan.priceEgp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Current plan badge
            if (isCurrentPlan) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(CoreRes.string.current_plan_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Limits section
            LimitsSection(plan.limits)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Features section
            FeaturesSection(plan.features)
        }
    }
}

@Composable
private fun LimitsSection(limits: PlanLimitsDto) {
    val unlimitedText = stringResource(CoreRes.string.unlimited)

    LimitRow(stringResource(CoreRes.string.plan_managers), limits.maxManagers, unlimitedText)
    LimitRow(stringResource(CoreRes.string.plan_cashiers), limits.maxCashiers, unlimitedText)
    LimitRow(stringResource(CoreRes.string.plan_delivery_persons), limits.maxDelivery, unlimitedText)
    LimitRow(stringResource(CoreRes.string.plan_orders_month), limits.maxOrdersPerMonth, unlimitedText)
    LimitRow(stringResource(CoreRes.string.plan_menu_items), limits.maxMenuItems, unlimitedText)
    LimitRow(stringResource(CoreRes.string.plan_branches), limits.maxBranches, unlimitedText)
}

@Composable
private fun LimitRow(label: String, value: Int, unlimitedText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (value == -1) unlimitedText else value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (value == -1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FeaturesSection(features: PlanFeaturesDto) {
    FeatureCheckRow(stringResource(CoreRes.string.plan_stock), features.stockManagement)
    FeatureCheckRow(stringResource(CoreRes.string.plan_attendance), features.workerAttendance)
    FeatureCheckRow(stringResource(CoreRes.string.plan_overtime), features.overtime)
    FeatureCheckRow(stringResource(CoreRes.string.plan_salaries), features.salaries)
    FeatureCheckRow(stringResource(CoreRes.string.plan_delivery_module), features.deliveryModule)
    FeatureCheckRow(stringResource(CoreRes.string.plan_customers), features.customerManagement)
    FeatureCheckRow(stringResource(CoreRes.string.plan_table_reservation), features.tableManagement)
    FeatureCheckRow(stringResource(CoreRes.string.plan_digital_receipt), features.digitalReceipt)
    FeatureCheckRow(stringResource(CoreRes.string.plan_worker_qrcode), features.workerQrcode)
    FeatureCheckRow(stringResource(CoreRes.string.plan_loyalty_points), features.loyaltyPoints)
    FeatureCheckRow(stringResource(CoreRes.string.plan_manual_discount), features.manualDiscount)
    FeatureCheckRow(stringResource(CoreRes.string.plan_offers_management), features.offersManagement)
    FeatureLevelRow(stringResource(CoreRes.string.plan_analytics), features.analytics)
    FeatureLevelRow(stringResource(CoreRes.string.plan_digital_menu), features.digitalMenu)
}

@Composable
private fun FeatureCheckRow(label: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (enabled) Color(0xFF4CAF50) else Color(0xFFE53935),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun FeatureLevelRow(label: String, level: String) {
    val enabled = level != "NONE"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (enabled) Color(0xFF4CAF50) else Color(0xFFE53935),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        if (enabled) {
            Text(
                text = level.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
