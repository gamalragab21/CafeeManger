package net.marllex.waselak.core.auth.di

import net.marllex.waselak.core.auth.AuthRepositoryImpl
import net.marllex.waselak.core.auth.TokenManager
import net.marllex.waselak.core.auth.TokenProviderImpl
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.auth.TokenProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module

val authModule = module {
    single { createDataStore(get<String>(qualifier = named("appName"))) }
    single { TokenManager(get()) }
    single<TokenProvider> { TokenProviderImpl(get(), lazy { get<WaselakApiClient>() }) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
}
