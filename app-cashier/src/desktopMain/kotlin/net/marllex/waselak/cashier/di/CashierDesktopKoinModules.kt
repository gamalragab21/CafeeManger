package net.marllex.waselak.cashier.di

import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.auth.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Modules registered at `startKoin {}` on desktop. Minimal — just platform bindings
 * and login. Post-login feature VMs live in `cashierFeaturesModule` (commonMain)
 * and are loaded lazily via `ensureCashierFeaturesLoaded()`.
 */
fun cashierDesktopKoinModules() = listOf(
    desktopPlatformModule,
    dispatchersModule,
    databaseModule,
    authModule,
    networkModule,
    dataModule,
    cashierAuthModule,
)

private val desktopPlatformModule = module {
    single { DatabaseDriverFactory() }
    single { NetworkMonitor() }
    single(named("baseUrl")) { BuildConfig.BASE_URL }
    single(named("appName")) { "cashier" }
    single(named("hmacSecret")) { BuildConfig.HMAC_SECRET }
}

/** Only VMs the login screen needs. Loaded at `startKoin`. */
private val cashierAuthModule = module {
    viewModelOf(::LoginViewModel)
}
