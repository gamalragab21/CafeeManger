package net.marllex.waselak.manager.di

import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.auth.LoginViewModel
import net.marllex.waselak.feature.cashier.receipt.ReceiptViewModel
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel
import net.marllex.waselak.feature.manager.categories.CategoriesViewModel
import net.marllex.waselak.feature.manager.customers.CustomersViewModel
import net.marllex.waselak.feature.manager.chatbot.di.chatbotModule
import net.marllex.waselak.feature.manager.dashboard.DashboardViewModel
import net.marllex.waselak.feature.manager.items.ItemsViewModel
import net.marllex.waselak.feature.manager.orders.OrdersViewModel
import net.marllex.waselak.feature.manager.staff.AnnouncementsViewModel
import net.marllex.waselak.feature.manager.staff.DeliveryDashboardViewModel
import net.marllex.waselak.feature.manager.staff.StaffViewModel
import net.marllex.waselak.feature.manager.staff.WorkerQrCodeViewModel
import net.marllex.waselak.feature.manager.stock.StockViewModel
import net.marllex.waselak.feature.manager.tables.TablesViewModel
import net.marllex.waselak.feature.manager.users.UsersViewModel
import net.marllex.waselak.manager.navigation.PlansComparisonViewModel
import net.marllex.waselak.manager.navigation.RestaurantProfileViewModel
import net.marllex.waselak.feature.manager.offers.OffersViewModel
import net.marllex.waselak.manager.customercredit.CustomerCreditViewModel
import net.marllex.waselak.manager.doctorstats.DoctorStatsViewModel
import net.marllex.waselak.manager.druginteractions.DrugInteractionsViewModel
import net.marllex.waselak.manager.notifications.NotificationsViewModel
import net.marllex.waselak.manager.offline.OfflineSettingsViewModel
import net.marllex.waselak.manager.returns.ReturnsViewModel
import net.marllex.waselak.manager.installments.InstallmentsViewModel
import net.marllex.waselak.manager.scheduledorders.ScheduledOrdersViewModel
import net.marllex.waselak.manager.suppliers.SuppliersViewModel
import net.marllex.waselak.manager.taxplaces.TaxPlacesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun managerIosKoinModules() = listOf(
    iosPlatformModule,
    dispatchersModule,
    databaseModule,
    authModule,
    networkModule,
    dataModule,
    chatbotModule,
    managerAppModule,
)

private val iosPlatformModule = module {
    single { DatabaseDriverFactory() }
    single { NetworkMonitor() }
    single(named("baseUrl")) { BuildConfig.BASE_URL }
    single(named("appName")) { "manager" }
    single(named("hmacSecret")) { BuildConfig.HMAC_SECRET }
}

private val managerAppModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::OrdersViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::ItemsViewModel)
    viewModelOf(::StockViewModel)
    viewModelOf(::TablesViewModel)
    viewModelOf(::UsersViewModel)
    viewModelOf(::StaffViewModel)
    viewModelOf(::WorkerQrCodeViewModel)
    viewModelOf(::AnnouncementsViewModel)
    viewModelOf(::DeliveryDashboardViewModel)
    viewModelOf(::AnalyticsViewModel)
    viewModelOf(::RestaurantProfileViewModel)
    viewModelOf(::PlansComparisonViewModel)
    viewModelOf(::TaxPlacesViewModel)
    viewModelOf(::ReceiptViewModel)
    viewModelOf(::CustomersViewModel)
    viewModelOf(::OfflineSettingsViewModel)
    viewModelOf(::OffersViewModel)
    viewModelOf(::SuppliersViewModel)
    viewModelOf(::ReturnsViewModel)
    viewModelOf(::InstallmentsViewModel)
    viewModelOf(::ScheduledOrdersViewModel)
    viewModelOf(::NotificationsViewModel)
    viewModelOf(::DrugInteractionsViewModel)
    viewModelOf(::CustomerCreditViewModel)
    viewModelOf(::DoctorStatsViewModel)
}
