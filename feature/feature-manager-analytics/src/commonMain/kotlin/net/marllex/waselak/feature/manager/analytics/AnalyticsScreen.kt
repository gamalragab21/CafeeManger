package net.marllex.waselak.feature.manager.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.marllex.waselak.feature.manager.analytics.components.AlertsSection
import net.marllex.waselak.feature.manager.analytics.components.CashierPerformanceSection
import net.marllex.waselak.feature.manager.analytics.components.CustomerIntelligenceSection
import net.marllex.waselak.feature.manager.analytics.components.DeliveryPerformanceSection
import net.marllex.waselak.feature.manager.analytics.components.ExecutiveSummaryCards
import net.marllex.waselak.feature.manager.analytics.components.ExportSection
import net.marllex.waselak.feature.manager.analytics.components.GlobalFilterBar
import net.marllex.waselak.feature.manager.analytics.components.OrdersIntelligenceSection
import net.marllex.waselak.feature.manager.analytics.components.PeakTimeSection
import net.marllex.waselak.feature.manager.analytics.components.ProductIntelligenceSection
import net.marllex.waselak.feature.manager.analytics.components.RevenueProfitSection
import net.marllex.waselak.feature.manager.analytics.components.StockOverviewSection
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val platformActions = rememberPlatformActions()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.analytics)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = {
                        viewModel.setTimePeriod(state.filters.timePeriod)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Global Filter Bar
            item {
                GlobalFilterBar(
                    selectedPeriod = state.filters.timePeriod,
                    onPeriodSelected = viewModel::setTimePeriod,
                )
            }

            // 2. Executive Summary
            item {
                ExecutiveSummaryCards(
                    state = state.executiveSummary,
                    onRetry = { viewModel.retrySection("executiveSummary") },
                )
            }

            // 3. Revenue & Profit
            item {
                RevenueProfitSection(
                    state = state.revenueProfit,
                    onRetry = { viewModel.retrySection("revenueProfit") },
                )
            }

            // 4. Orders Intelligence
            item {
                OrdersIntelligenceSection(
                    state = state.ordersIntelligence,
                    onRetry = { viewModel.retrySection("ordersIntelligence") },
                )
            }

            // 5. Peak Time Analysis
            item {
                PeakTimeSection(
                    state = state.peakTimeAnalysis,
                    onRetry = { viewModel.retrySection("peakTimeAnalysis") },
                )
            }

            // 6. Cashier Performance
            item {
                CashierPerformanceSection(
                    state = state.cashierPerformance,
                    onRetry = { viewModel.retrySection("cashierPerformance") },
                )
            }

            // 7. Delivery Performance
            item {
                DeliveryPerformanceSection(
                    state = state.deliveryPerformance,
                    onRetry = { viewModel.retrySection("deliveryPerformance") },
                )
            }

            // 8. Product Intelligence
            item {
                ProductIntelligenceSection(
                    state = state.productIntelligence,
                    onRetry = { viewModel.retrySection("productIntelligence") },
                )
            }

            // 9. Customer Intelligence
            item {
                CustomerIntelligenceSection(
                    state = state.customerIntelligence,
                    onRetry = { viewModel.retrySection("customerIntelligence") },
                )
            }

            // 10. Alerts & Risks
            item {
                AlertsSection(
                    state = state.alerts,
                    onRetry = { viewModel.retrySection("alerts") },
                )
            }

            // 11. Stock Overview
            item {
                StockOverviewSection(
                    state = state.stockOverview,
                    onRetry = { viewModel.retrySection("stockOverview") },
                )
            }

            // 12. Export Report
            item {
                val fileSaver: (ByteArray, String) -> String = { bytes, name ->
                    platformActions.saveFileToDownloads(bytes, name)
                }
                ExportSection(
                    exportState = state.exportState,
                    onExportPDF = { viewModel.exportPDF(fileSaver) },
                    onExportExcel = { viewModel.exportExcel(fileSaver) },
                    onClearExportState = viewModel::clearExportState,
                )
            }

            // Bottom spacing
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
