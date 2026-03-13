package net.marllex.waselak.delivery.di

import android.content.Context
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.auth.LoginViewModel
import net.marllex.waselak.feature.delivery.map.DeliveryMapViewModel
import net.marllex.waselak.feature.delivery.orders.DeliveryOrdersViewModel
import net.marllex.waselak.feature.delivery.orders.DeliveryReceiptViewModel
import net.marllex.waselak.feature.delivery.orders.history.DeliveryHistoryViewModel
import net.marllex.waselak.feature.delivery.status.DeliveryStatusViewModel
import net.marllex.waselak.delivery.notifications.DeliveryNotificationsViewModel
import net.marllex.waselak.feature.manager.staff.AnnouncementsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun deliveryKoinModules() = listOf(
    platformModule,
    dispatchersModule,
    databaseModule,
    authModule,
    networkModule,
    dataModule,
    deliveryAppModule,
)

private val platformModule = module {
    single { DatabaseDriverFactory(get<Context>()) }
    single(named("baseUrl")) { BuildConfig.BASE_URL }
    single(named("appName")) { "delivery" }
    single(named("hmacSecret")) { BuildConfig.HMAC_SECRET }
}

private val deliveryAppModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::DeliveryOrdersViewModel)
    viewModelOf(::DeliveryHistoryViewModel)
    viewModelOf(::DeliveryMapViewModel)
    viewModelOf(::DeliveryStatusViewModel)
    viewModelOf(::AnnouncementsViewModel)
    viewModelOf(::DeliveryReceiptViewModel)
    viewModelOf(::DeliveryNotificationsViewModel)
}
