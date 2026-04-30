package net.marllex.waselak.backend

import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.config.DatabaseConfig
import net.marllex.waselak.backend.di.appModule
import net.marllex.waselak.backend.data.database.InstallmentPaymentsTable
import net.marllex.waselak.backend.data.database.InstallmentPlansTable
import net.marllex.waselak.backend.data.database.VendorSubscriptionsTable
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.domain.service.NotificationService
import net.marllex.waselak.backend.domain.service.RequestLogService
import net.marllex.waselak.backend.plugins.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

    // Janitor for orphan create-org modal uploads. The super-admin
    // create-org modal uploads a logo BEFORE the org is created (we don't
    // have an orgId yet), naming the file `pending-<uuid>.png`. If the
    // operator submits the form, the URL is persisted in `crm_organizations.logo_url`
    // and the file is referenced. If they cancel, the file is orphaned —
    // never referenced from any DB row but consuming disk forever. This
    // sweep deletes any `pending-*` file in uploads/crm-photos/ older than
    // an hour, which is far longer than any realistic edit-modal session.
    fixedRateTimer("pending-logo-cleanup", daemon = true, initialDelay = 5 * 60_000L, period = 60 * 60_000L) {
        try {
            val dir = File("uploads/crm-photos")
            if (!dir.exists()) return@fixedRateTimer
            val cutoff = System.currentTimeMillis() - 60 * 60_000L  // 1h
            dir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("pending-") && it.lastModified() < cutoff }
                ?.forEach { runCatching { it.delete() } }
        } catch (_: Exception) {
            // Cleanup is best-effort; never let a transient FS error kill the timer.
        }
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

    // Schedule installment overdue check + auto late fee (daily, 3-minute initial delay)
    fixedRateTimer("installment-overdue-check", daemon = true, initialDelay = 180_000L, period = 86_400_000L) {
        try {
            val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            transaction {
                // 1. Mark PENDING payments as OVERDUE if due date has passed
                val pendingOverdue = InstallmentPaymentsTable.selectAll().where {
                    (InstallmentPaymentsTable.status inList listOf("PENDING", "PARTIALLY_PAID")) and
                        (InstallmentPaymentsTable.dueDate less nowMs)
                }.toList()

                for (payment in pendingOverdue) {
                    val currentStatus = payment[InstallmentPaymentsTable.status]
                    if (currentStatus == "PENDING") {
                        InstallmentPaymentsTable.update({
                            InstallmentPaymentsTable.id eq payment[InstallmentPaymentsTable.id]
                        }) {
                            it[InstallmentPaymentsTable.status] = "OVERDUE"
                        }
                    }
                }

                // 2. Auto-apply late fees where lateFeeEnabled=true, lateFee=0, overdue
                val overdueNoFee = InstallmentPaymentsTable.selectAll().where {
                    (InstallmentPaymentsTable.status inList listOf("OVERDUE", "PARTIALLY_PAID")) and
                        (InstallmentPaymentsTable.dueDate less nowMs) and
                        (InstallmentPaymentsTable.lateFee eq java.math.BigDecimal.ZERO) and
                        (InstallmentPaymentsTable.lateFeeEnabled eq true)
                }.toList()

                for (payment in overdueNoFee) {
                    val planId = payment[InstallmentPaymentsTable.planId]
                    val plan = InstallmentPlansTable.selectAll().where {
                        InstallmentPlansTable.id eq planId
                    }.firstOrNull() ?: continue

                    val feePercent = plan[InstallmentPlansTable.lateFeePercent].toDouble()
                    if (feePercent <= 0) continue

                    val remainingDue = payment[InstallmentPaymentsTable.amount].toDouble() -
                        payment[InstallmentPaymentsTable.paidAmount].toDouble()
                    val fee = Math.round(remainingDue * feePercent / 100.0 * 100.0) / 100.0

                    InstallmentPaymentsTable.update({
                        InstallmentPaymentsTable.id eq payment[InstallmentPaymentsTable.id]
                    }) {
                        it[InstallmentPaymentsTable.lateFee] = java.math.BigDecimal.valueOf(fee)
                    }

                    val currentRemaining = plan[InstallmentPlansTable.remainingAmount].toDouble()
                    InstallmentPlansTable.update({
                        InstallmentPlansTable.id eq planId
                    }) {
                        it[InstallmentPlansTable.remainingAmount] = java.math.BigDecimal.valueOf(currentRemaining + fee)
                        it[InstallmentPlansTable.updatedAt] = nowMs
                    }
                }

                log.info("Installment check: ${pendingOverdue.size} marked overdue, ${overdueNoFee.size} late fees applied")
            }
        } catch (e: Exception) {
            log.error("Installment overdue check failed: ${e.message}")
        }
    }
}
