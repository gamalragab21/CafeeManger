package net.marllex.waselak.kds.di

import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.auth.LoginViewModel
import net.marllex.waselak.kds.display.KdsDisplayViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun kdsDesktopKoinModules() = listOf(
    desktopPlatformModule,
    dispatchersModule,
    databaseModule,
    authModule,
    networkModule,
    dataModule,
    kdsAppModule,
)

private val desktopPlatformModule = module {
    single { DatabaseDriverFactory() }
    single { NetworkMonitor() }
    single(named("baseUrl")) { BuildConfig.BASE_URL }
    single(named("appName")) { "kds" }
    single(named("hmacSecret")) { BuildConfig.HMAC_SECRET }
}

private val kdsAppModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::KdsDisplayViewModel)
}
