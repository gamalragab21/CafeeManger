package net.marllex.waselak.cashier.di

import android.content.Context
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.auth.LoginViewModel
import net.marllex.waselak.feature.cashier.attendance.AttendanceViewModel
import net.marllex.waselak.feature.cashier.payment.PaymentViewModel
import net.marllex.waselak.feature.cashier.pos.PosViewModel
import net.marllex.waselak.feature.cashier.receipt.ReceiptViewModel
import net.marllex.waselak.feature.manager.orders.OrdersViewModel
import net.marllex.waselak.feature.manager.staff.AnnouncementsViewModel
import net.marllex.waselak.feature.manager.staff.DeliveryDashboardViewModel
import net.marllex.waselak.feature.manager.tables.TablesViewModel
import net.marllex.waselak.cashier.cashdrawer.CashDrawerViewModel
import net.marllex.waselak.cashier.customercredit.CashierCustomerCreditViewModel
import net.marllex.waselak.cashier.returns.ReturnsViewModel
import net.marllex.waselak.cashier.kds.KdsViewModel
import net.marllex.waselak.cashier.notifications.CashierNotificationsViewModel
import net.marllex.waselak.cashier.prescriptions.PrescriptionsViewModel
import net.marllex.waselak.cashier.splitpayment.SplitPaymentViewModel
import net.marllex.waselak.cashier.scheduledorders.ScheduledOrdersViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun cashierKoinModules() = listOf(
    platformModule,
    dispatchersModule,
    databaseModule,
    authModule,
    networkModule,
    dataModule,
    cashierAppModule,
)

private val platformModule = module {
    single { DatabaseDriverFactory(get<Context>()) }
    single { NetworkMonitor(get<Context>()) }
    single(named("baseUrl")) { BuildConfig.BASE_URL }
    single(named("appName")) { "cashier" }
    single(named("hmacSecret")) { BuildConfig.HMAC_SECRET }
}

private val cashierAppModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::PosViewModel)
    viewModelOf(::PaymentViewModel)
    viewModelOf(::AttendanceViewModel)
    viewModelOf(::OrdersViewModel)
    viewModelOf(::TablesViewModel)
    viewModelOf(::AnnouncementsViewModel)
    viewModelOf(::DeliveryDashboardViewModel)
    viewModelOf(::ReceiptViewModel)
    viewModelOf(::KdsViewModel)
    viewModelOf(::CashDrawerViewModel)
    viewModelOf(::CashierNotificationsViewModel)
    viewModelOf(::ScheduledOrdersViewModel)
    viewModelOf(::PrescriptionsViewModel)
    viewModelOf(::SplitPaymentViewModel)
    viewModelOf(::CashierCustomerCreditViewModel)
    viewModelOf(::ReturnsViewModel)
}
