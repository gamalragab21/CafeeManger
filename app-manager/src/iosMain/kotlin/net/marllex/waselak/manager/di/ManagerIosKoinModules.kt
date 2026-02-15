package net.marllex.waselak.manager.di

import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.manager.chatbot.di.chatbotModule
import net.marllex.waselak.manager.navigation.RestaurantProfileViewModel
import net.marllex.waselak.manager.taxplaces.TaxPlacesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun managerIosKoinModules() = listOf(
    iosPlatformModule,
    dispatchersModule,
    networkModule,
    databaseModule,
    authModule,
    dataModule,
    chatbotModule,
    managerAppModule,
)

private val iosPlatformModule = module {
    single { DatabaseDriverFactory() }
    single(named("baseUrl")) { "https://api.waselak.net/" }
}

private val managerAppModule = module {
    viewModelOf(::RestaurantProfileViewModel)
    viewModelOf(::TaxPlacesViewModel)
}
