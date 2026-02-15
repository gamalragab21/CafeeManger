package net.marllex.waselak.core.auth.di

import net.marllex.waselak.core.auth.AuthRepositoryImpl
import net.marllex.waselak.core.auth.TokenManager
import net.marllex.waselak.core.domain.repository.AuthRepository
import org.koin.dsl.module

val authModule = module {
    single { createDataStore() }
    single { TokenManager(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
}
