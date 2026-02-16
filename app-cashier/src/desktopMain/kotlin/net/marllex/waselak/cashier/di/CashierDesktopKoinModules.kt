package net.marllex.waselak.cashier.di

import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.auth.LoginViewModel
import net.marllex.waselak.feature.cashier.attendance.AttendanceViewModel
import net.marllex.waselak.feature.cashier.payment.PaymentViewModel
import net.marllex.waselak.feature.cashier.pos.PosViewModel
import net.marllex.waselak.feature.manager.orders.OrdersViewModel
import net.marllex.waselak.feature.manager.staff.AnnouncementsViewModel
import net.marllex.waselak.feature.manager.staff.DeliveryDashboardViewModel
import net.marllex.waselak.feature.manager.tables.TablesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun cashierDesktopKoinModules() = listOf(
    desktopPlatformModule,
    dispatchersModule,
    networkModule,
    databaseModule,
    authModule,
    dataModule,
    cashierAppModule,
)

private val desktopPlatformModule = module {
    single { DatabaseDriverFactory() }
    single(named("baseUrl")) { BuildConfig.BASE_URL }
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
}
