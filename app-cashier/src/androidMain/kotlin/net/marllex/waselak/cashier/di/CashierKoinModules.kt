package net.marllex.waselak.cashier.di

import android.content.Context
import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.di.networkModule
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun cashierKoinModules() = listOf(
    platformModule,
    dispatchersModule,
    networkModule,
    databaseModule,
    authModule,
    dataModule,
)

private val platformModule = module {
    single { DatabaseDriverFactory(get<Context>()) }
    single(named("baseUrl")) { "https://api.waselak.net/" }
}
