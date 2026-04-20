package net.marllex.waselak.cashier.di

import net.marllex.waselak.cashier.cashdrawer.CashDrawerViewModel
import net.marllex.waselak.cashier.customercredit.CashierCustomerCreditViewModel
import net.marllex.waselak.cashier.installments.CashierInstallmentsViewModel
import net.marllex.waselak.cashier.kds.KdsViewModel
import net.marllex.waselak.cashier.notifications.CashierNotificationsViewModel
import net.marllex.waselak.cashier.prescriptions.PrescriptionsViewModel
import net.marllex.waselak.cashier.returns.ReturnsViewModel
import net.marllex.waselak.cashier.scheduledorders.ScheduledOrdersViewModel
import net.marllex.waselak.cashier.splitpayment.SplitPaymentViewModel
import net.marllex.waselak.feature.cashier.attendance.AttendanceViewModel
import net.marllex.waselak.feature.cashier.payment.PaymentViewModel
import net.marllex.waselak.feature.cashier.pos.PosViewModel
import net.marllex.waselak.feature.cashier.receipt.ReceiptViewModel
import net.marllex.waselak.feature.manager.orders.OrdersViewModel
import net.marllex.waselak.feature.manager.staff.AnnouncementsViewModel
import net.marllex.waselak.feature.manager.staff.DeliveryDashboardViewModel
import net.marllex.waselak.feature.manager.tables.TablesViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Post-login feature VMs. Pulled out of the startup module so the login screen
 * opens before these bindings are registered. Loaded exactly once via
 * [ensureCashierFeaturesLoaded] when the user enters the post-login nav graph.
 */
internal val cashierFeaturesModule: Module = module {
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
    viewModelOf(::CashierInstallmentsViewModel)
}

// Compose composition is single-threaded on the main thread; a @Volatile flag is
// enough to guard against double-registration from a recomposition storm.
@Volatile
private var featuresLoaded = false

/**
 * Idempotent. Call once the user is past login. Cheap — module registration is a
 * handful of map insertions — but it means login-only sessions (wrong vendor,
 * forced logout, etc.) never pay the cost.
 */
fun ensureCashierFeaturesLoaded() {
    if (featuresLoaded) return
    featuresLoaded = true
    GlobalContext.get().loadModules(listOf(cashierFeaturesModule))
}
