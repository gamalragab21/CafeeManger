package net.marllex.cafeemanger.backend.data.migrations

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marllex.cafeemanger.backend.data.database.WorkersTable
import net.marllex.cafeemanger.backend.domain.service.PinService
import net.marllex.cafeemanger.backend.domain.service.QrCodeService
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Migration script to generate PINs and QR codes for existing workers
 * 
 * This should be run once after deploying the PIN/QR authentication feature
 * to ensure all existing workers have PINs and QR codes.
 * 
 * Usage:
 * - Run this as a one-time script after deployment
 * - Default PINs will be generated (last 4 digits of phone or random)
 * - Managers should be notified to update worker PINs
 */
object WorkerPinQrMigration {
    
    data class MigrationResult(
        val totalWorkers: Int,
        val workersUpdated: Int,
        val defaultPins: Map<String, String> // worker_id to default PIN
    )

    /**
     * Migrate all existing workers to have PINs and QR codes
     */
    fun migrate(pinService: PinService, qrCodeService: QrCodeService): MigrationResult {
        val defaultPins = mutableMapOf<String, String>()
        var totalWorkers = 0
        var workersUpdated = 0

        transaction {
            // Get all workers without PIN or QR code
            val workers = WorkersTable.selectAll()
                .where { 
                    WorkersTable.pinHash.isNull() or WorkersTable.qrCodeData.isNull()
                }
                .toList()

            totalWorkers = workers.size

            workers.forEach { worker ->
                val workerId = worker[WorkersTable.id].toString()
                val workerIdReadable = worker[WorkersTable.workerId]
                val fullName = worker[WorkersTable.fullName]
                val phone = worker[WorkersTable.phone]
                val role = worker[WorkersTable.role]
                val now = Clock.System.now()

                try {
                    // Generate default PIN if not exists
                    val needsPin = worker[WorkersTable.pinHash] == null
                    val defaultPin = if (needsPin) {
                        pinService.generateDefaultPin(workerId, phone)
                    } else null

                    // Generate QR code if not exists
                    val needsQr = worker[WorkersTable.qrCodeData] == null
                    val qrDataJson = if (needsQr) {
                        val qrData = qrCodeService.generateQrCodeData(
                            workerId = workerId,
                            name = fullName,
                            role = role,
                            version = 1
                        )
                        Json.encodeToString(qrData)
                    } else null

                    // Update worker
                    WorkersTable.update({ WorkersTable.id eq worker[WorkersTable.id] }) {
                        if (needsPin && defaultPin != null) {
                            it[WorkersTable.pinHash] = pinService.hashPin(defaultPin)
                            it[WorkersTable.pinUpdatedAt] = now
                        }
                        if (needsQr && qrDataJson != null) {
                            it[WorkersTable.qrCodeData] = qrDataJson
                            it[WorkersTable.qrCodeVersion] = 1
                        }
                        it[WorkersTable.updatedAt] = now
                    }

                    if (defaultPin != null) {
                        defaultPins[workerIdReadable] = defaultPin
                    }
                    workersUpdated++

                    println("✓ Migrated worker: $workerIdReadable ($fullName)")
                } catch (e: Exception) {
                    println("✗ Failed to migrate worker: $workerIdReadable - ${e.message}")
                }
            }
        }

        return MigrationResult(
            totalWorkers = totalWorkers,
            workersUpdated = workersUpdated,
            defaultPins = defaultPins
        )
    }

    /**
     * Print migration results in a readable format
     */
    fun printResults(result: MigrationResult) {
        println("\n" + "=".repeat(60))
        println("Worker PIN/QR Migration Results")
        println("=".repeat(60))
        println("Total workers processed: ${result.totalWorkers}")
        println("Workers updated: ${result.workersUpdated}")
        println("\nDefault PINs generated:")
        println("-".repeat(60))
        
        if (result.defaultPins.isEmpty()) {
            println("No default PINs generated (all workers already had PINs)")
        } else {
            result.defaultPins.forEach { (workerId, pin) ->
                println("Worker $workerId: $pin")
            }
            println("\n⚠️  IMPORTANT: Notify managers to update these default PINs!")
            println("⚠️  Workers should change their PINs from the default values.")
        }
        println("=".repeat(60) + "\n")
    }

    /**
     * Export default PINs to a CSV file for manager notification
     */
    fun exportDefaultPinsCsv(result: MigrationResult): String {
        val csv = StringBuilder()
        csv.appendLine("Worker ID,Default PIN,Action Required")
        result.defaultPins.forEach { (workerId, pin) ->
            csv.appendLine("$workerId,$pin,Update PIN in worker settings")
        }
        return csv.toString()
    }
}

/**
 * Main function to run the migration
 * Can be executed as a standalone script or called from application startup
 */
fun main() {
    val pinService = PinService()
    val qrCodeService = QrCodeService()
    
    println("Starting worker PIN/QR migration...")
    val result = WorkerPinQrMigration.migrate(pinService, qrCodeService)
    WorkerPinQrMigration.printResults(result)
    
    // Export CSV
    val csv = WorkerPinQrMigration.exportDefaultPinsCsv(result)
    println("CSV Export:")
    println(csv)
}
