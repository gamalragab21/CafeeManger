package net.marllex.waselak.backend.di

import io.ktor.server.config.*
import net.marllex.waselak.backend.config.HmacConfig
import net.marllex.waselak.backend.config.JwtConfig
import net.marllex.waselak.backend.domain.service.AuthService
import net.marllex.waselak.backend.domain.service.OrderService
import net.marllex.waselak.backend.domain.service.PinService
import net.marllex.waselak.backend.domain.service.QrCodeService
import org.koin.dsl.module

val appModule = module {
    single { HmacConfig(get<ApplicationConfig>()) }
    single { JwtConfig(get<ApplicationConfig>()) }
    single { AuthService(get()) }
    single { OrderService() }
    single { PinService() }
    single { QrCodeService() }
    single { net.marllex.waselak.backend.domain.service.ExportService() }
}
