package net.marllex.cafeemanger.backend.di

import io.ktor.server.config.*
import net.marllex.cafeemanger.backend.config.JwtConfig
import net.marllex.cafeemanger.backend.domain.service.AuthService
import net.marllex.cafeemanger.backend.domain.service.OrderService
import net.marllex.cafeemanger.backend.domain.service.PinService
import net.marllex.cafeemanger.backend.domain.service.QrCodeService
import org.koin.dsl.module

val appModule = module {
    single { JwtConfig(get<ApplicationConfig>()) }
    single { AuthService(get()) }
    single { OrderService() }
    single { PinService() }
    single { QrCodeService() }
}
