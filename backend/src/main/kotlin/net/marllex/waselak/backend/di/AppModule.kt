package net.marllex.waselak.backend.di

import io.ktor.server.config.*
import net.marllex.waselak.backend.config.AdminJwtConfig
import net.marllex.waselak.backend.config.HmacConfig
import net.marllex.waselak.backend.config.JwtConfig
import net.marllex.waselak.backend.domain.service.*
import org.koin.dsl.module

val appModule = module {
    single { HmacConfig(get<ApplicationConfig>()) }
    single { JwtConfig(get<ApplicationConfig>()) }
    single { AdminJwtConfig(get<ApplicationConfig>()) }
    single { AuthService(get()) }
    single { AdminAuthService(get()) }
    single { CrmService(get()) }
    single { RequestLogService() }
    single { PlanService() }
    single { OrderService() }
    single { PinService() }
    single { QrCodeService() }
    single { net.marllex.waselak.backend.domain.service.ExportService() }
    single { AnalyticsQueryService() }
    single { NotificationService() }
}
