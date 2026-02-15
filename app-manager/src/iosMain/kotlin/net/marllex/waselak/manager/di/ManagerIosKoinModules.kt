package net.marllex.waselak.manager.di

import net.marllex.waselak.core.auth.di.authModule
import net.marllex.waselak.core.common.di.dispatchersModule
import net.marllex.waselak.core.data.di.dataModule
import net.marllex.waselak.core.database.di.DatabaseDriverFactory
import net.marllex.waselak.core.database.di.databaseModule
import net.marllex.waselak.core.network.di.networkModule
import net.marllex.waselak.feature.manager.chatbot.di.chatbotModule
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
)

private val iosPlatformModule = module {
    single { DatabaseDriverFactory() }
    single(named("baseUrl")) { "https://api.waselak.net/" }
}
