package net.marllex.waselak.backend

import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.config.DatabaseConfig
import net.marllex.waselak.backend.di.appModule
import net.marllex.waselak.backend.domain.service.RequestLogService
import net.marllex.waselak.backend.plugins.*
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.io.File
import kotlin.concurrent.fixedRateTimer

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Initialize Koin DI
    install(Koin) {
        slf4jLogger()
        modules(
            module {
                single<ApplicationConfig> { environment.config }
            },
            appModule
        )
    }

    // Initialize database
    DatabaseConfig.init(environment.config, false)

    // Configure plugins
    configureSerialization()
    configureAuthentication()
    configureCORS()
    configureHmacVerification()
    configureStatusPages()
    configureCallLogging()
    configureRequestLogging()
    configureRouting()

    // Serve uploaded files statically
    val uploadsDir = File("uploads")
    if (!uploadsDir.exists()) uploadsDir.mkdirs()

    // Serve landing page
    val publicDir = File("public")
    if (!publicDir.exists()) publicDir.mkdirs()

    routing {
        staticFiles("/uploads", uploadsDir)
        staticFiles("/landing", publicDir) {
            default("index.html")
        }
    }

    // Schedule log cleanup (daily)
    val retentionDays = environment.config.propertyOrNull("logging.retentionDays")?.getString()?.toIntOrNull() ?: 30
    val requestLogService by inject<RequestLogService>()
    fixedRateTimer("log-cleanup", daemon = true, initialDelay = 60_000L, period = 86_400_000L) {
        requestLogService.cleanupOldLogs(retentionDays)
    }
}
