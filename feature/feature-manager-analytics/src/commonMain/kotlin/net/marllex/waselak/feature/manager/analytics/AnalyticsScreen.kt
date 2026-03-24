package net.marllex.waselak.feature.manager.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.marllex.waselak.feature.manager.analytics.components.AlertsSection
import net.marllex.waselak.feature.manager.analytics.components.CashierPerformanceSection
import net.marllex.waselak.feature.manager.analytics.components.CustomerIntelligenceSection
import net.marllex.waselak.feature.manager.analytics.components.DeliveryPerformanceSection
import net.marllex.waselak.feature.manager.analytics.components.DiscountAnalyticsSection
import net.marllex.waselak.feature.manager.analytics.components.LoyaltyAnalyticsSection
import net.marllex.waselak.feature.manager.analytics.components.OffersAnalyticsSection
import net.marllex.waselak.feature.manager.analytics.components.ExecutiveSummaryCards
import net.marllex.waselak.feature.manager.analytics.components.CreditAnalyticsSection
import net.marllex.waselak.feature.manager.analytics.components.ReturnsAnalyticsSection
import net.marllex.waselak.feature.manager.analytics.components.DoctorSummarySection
import net.marllex.waselak.feature.manager.analytics.components.ExportSection
import net.marllex.waselak.feature.manager.analytics.components.GlobalFilterBar
import net.marllex.waselak.feature.manager.analytics.components.OrdersIntelligenceSection
import net.marllex.waselak.feature.manager.analytics.components.PeakTimeSection
import net.marllex.waselak.feature.manager.analytics.components.ProductIntelligenceSection
import net.marllex.waselak.feature.manager.analytics.components.RevenueProfitSection
import net.marllex.waselak.feature.manager.analytics.components.StaffCostsSection
import net.marllex.waselak.feature.manager.analytics.components.StockOverviewSection
import net.marllex.waselak.feature.manager.analytics.components.SupplierAnalyticsSection
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import org.koin.compose.viewmodel.koinViewModel

private data class AnalyticsTab(val title: String, val key: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    businessType: String? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val platformActions = rememberPlatformActions()
    val scope = rememberCoroutineScope()

    val isPharmacyOrRetail = businessType in listOf("PHARMACY", "RETAIL")

    // Build tabs dynamically based on business type
    val tabs = remember(isPharmacyOrRetail) {
        buildList {
            add(AnalyticsTab("Overview", "overview"))
            add(AnalyticsTab("Revenue", "revenue"))
            add(AnalyticsTab("Team", "team"))
            add(AnalyticsTab("Products", "products"))
            add(AnalyticsTab("Customers", "customers"))
            if (isPharmacyOrRetail) {
                add(AnalyticsTab("Pharmacy", "pharmacy"))
            }
            add(AnalyticsTab("Alerts", "alerts"))
            add(AnalyticsTab("Export", "export"))
        }
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.analytics),
                isLoading = state.executiveSummary is AnalyticsViewModel.SectionState.Loading,
                onRefresh = viewModel::loadAllSections,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // Global Filter Bar (always visible)
            GlobalFilterBar(
                selectedPeriod = state.filters.timePeriod,
                onPeriodSelected = viewModel::setTimePeriod,
                customFromDate = state.filters.fromDate,
                customToDate = state.filters.toDate,
                onCustomDateRange = viewModel::setCustomDateRange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                tab.title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tabKey = tabs[page].key
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (tabKey) {
                        "overview" -> {
                            item {
                                ExecutiveSummaryCards(
                                    state = state.executiveSummary,
                                    onRetry = { viewModel.retrySection("executiveSummary") },
                                )
                            }
                        }

                        "revenue" -> {
                            item {
                                RevenueProfitSection(
                                    state = state.revenueProfit,
                                    onRetry = { viewModel.retrySection("revenueProfit") },
                                )
                            }
                            item {
                                OrdersIntelligenceSection(
                                    state = state.ordersIntelligence,
                                    onRetry = { viewModel.retrySection("ordersIntelligence") },
                                )
                            }
                            item {
                                PeakTimeSection(
                                    state = state.peakTimeAnalysis,
                                    onRetry = { viewModel.retrySection("peakTimeAnalysis") },
                                )
                            }
                        }

                        "team" -> {
                            item {
                                CashierPerformanceSection(
                                    state = state.cashierPerformance,
                                    onRetry = { viewModel.retrySection("cashierPerformance") },
                                )
                            }
                            item {
                                DeliveryPerformanceSection(
                                    state = state.deliveryPerformance,
                                    onRetry = { viewModel.retrySection("deliveryPerformance") },
                                )
                            }
                            item {
                                StaffCostsSection(
                                    state = state.staffCosts,
                                    onRetry = { viewModel.retrySection("staffCosts") },
                                )
                            }
                        }

                        "products" -> {
                            item {
                                ProductIntelligenceSection(
                                    state = state.productIntelligence,
                                    onRetry = { viewModel.retrySection("productIntelligence") },
                                )
                            }
                            item {
                                StockOverviewSection(
                                    state = state.stockOverview,
                                    onRetry = { viewModel.retrySection("stockOverview") },
                                )
                            }
                            item {
                                SupplierAnalyticsSection(
                                    state = state.supplierAnalytics,
                                    onRetry = { viewModel.retrySection("supplierAnalytics") },
                                )
                            }
                        }

                        "customers" -> {
                            item {
                                CustomerIntelligenceSection(
                                    state = state.customerIntelligence,
                                    onRetry = { viewModel.retrySection("customerIntelligence") },
                                )
                            }
                            item {
                                OffersAnalyticsSection(
                                    state = state.offersAnalytics,
                                    onRetry = { viewModel.retrySection("offersAnalytics") },
                                )
                            }
                            item {
                                DiscountAnalyticsSection(
                                    state = state.discountAnalytics,
                                    onRetry = { viewModel.retrySection("discountAnalytics") },
                                )
                            }
                            item {
                                LoyaltyAnalyticsSection(
                                    state = state.loyaltyAnalytics,
                                    onRetry = { viewModel.retrySection("loyaltyAnalytics") },
                                )
                            }
                        }

                        "pharmacy" -> {
                            item {
                                val creditState = state.creditAnalytics
                                if (creditState is AnalyticsViewModel.SectionState.Success) {
                                    CreditAnalyticsSection(data = creditState.data)
                                }
                            }
                            item {
                                val doctorState = state.doctorStats
                                if (doctorState is AnalyticsViewModel.SectionState.Success && doctorState.data.isNotEmpty()) {
                                    DoctorSummarySection(doctors = doctorState.data)
                                }
                            }
                            item {
                                val returnsState = state.returnsAnalytics
                                if (returnsState is AnalyticsViewModel.SectionState.Success && returnsState.data.totalReturns > 0) {
                                    ReturnsAnalyticsSection(data = returnsState.data)
                                }
                            }
                        }

                        "alerts" -> {
                            item {
                                AlertsSection(
                                    state = state.alerts,
                                    onRetry = { viewModel.retrySection("alerts") },
                                )
                            }
                        }

                        "export" -> {
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
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
