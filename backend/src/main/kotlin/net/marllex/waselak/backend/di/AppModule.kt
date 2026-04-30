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
    // ExportService now depends on AnalyticsQueryService to collect every analytics
    // section (revenue, cashiers, products, customers, alerts, stock, etc.) into one
    // multi-sheet Excel / multi-section PDF. Declare the query service first so Koin
    // can resolve the dependency.
    single { AnalyticsQueryService() }
    single { net.marllex.waselak.backend.domain.service.ExportService(get()) }
    single { NotificationService() }
}
