package net.marllex.waselak.backend

import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.config.DatabaseConfig
import net.marllex.waselak.backend.di.appModule
import net.marllex.waselak.backend.data.database.VendorSubscriptionsTable
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.domain.service.NotificationService
import net.marllex.waselak.backend.domain.service.RequestLogService
import net.marllex.waselak.backend.plugins.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days

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
    configureSentry()
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

    // Schedule subscription expiry checks (daily, 2-minute initial delay)
    val notificationService by inject<NotificationService>()
    fixedRateTimer("subscription-check", daemon = true, initialDelay = 120_000L, period = 86_400_000L) {
        try {
            val now = kotlinx.datetime.Clock.System.now()
            val sevenDaysFromNow = now.plus(7.days)
            val twoDaysFromNow = now.plus(2.days)

            transaction {
                val activeSubs = VendorSubscriptionsTable.selectAll().where {
                    VendorSubscriptionsTable.status eq "ACTIVE"
                }.toList()

                for (sub in activeSubs) {
                    val expiresAt = sub[VendorSubscriptionsTable.expiresAt] ?: continue
                    val vendorId = sub[VendorSubscriptionsTable.vendorId]

                    when {
                        // Already expired
                        expiresAt <= now -> {
                            notificationService.broadcast(
                                vendorId = vendorId,
                                type = "SUBSCRIPTION_EXPIRED",
                                title = "Subscription Expired",
                                body = "Your subscription has expired. Please renew to continue using all features.",
                                priority = "URGENT",
                                actionUrl = "/settings/subscription",
                            )
                        }
                        // Expires within 2 days
                        expiresAt <= twoDaysFromNow -> {
                            notificationService.broadcast(
                                vendorId = vendorId,
                                type = "SUBSCRIPTION_EXPIRING",
                                title = "Subscription Expiring Soon",
                                body = "Your subscription expires in less than 2 days. Please renew now.",
                                priority = "HIGH",
                                actionUrl = "/settings/subscription",
                            )
                        }
                        // Expires within 7 days
                        expiresAt <= sevenDaysFromNow -> {
                            notificationService.broadcast(
                                vendorId = vendorId,
                                type = "SUBSCRIPTION_EXPIRING",
                                title = "Subscription Expiring",
                                body = "Your subscription will expire in 7 days. Consider renewing.",
                                priority = "NORMAL",
                                actionUrl = "/settings/subscription",
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Subscription check failed: ${e.message}")
        }
    }
}
