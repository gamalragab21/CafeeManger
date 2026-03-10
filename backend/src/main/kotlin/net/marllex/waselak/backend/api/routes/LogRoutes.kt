package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class LogUploadResponse(
    val message: String,
    val filename: String,
)

fun Route.logRoutes() {
    route("/api/v1/logs") {
        post("/upload") {
            val trace = call.routeTrace()
            trace.step("Log upload started")
            val principal = requireRole("MANAGER", "CASHIER", "DELIVERY")
            trace.step("Authenticated", mapOf("vendorId" to principal.vendorId, "role" to principal.role))

            // Get vendor name for folder organization
            trace.step("Looking up vendor name")
            val vendorName = transaction {
                VendorsTable.selectAll()
                    .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                    .firstOrNull()?.get(VendorsTable.name)
            } ?: "unknown"

            // Create sanitized folder name: vendorname-vendorid
            val sanitizedVendorName = vendorName.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF-_ ]"), "").trim().replace(" ", "-").take(50)
            val vendorFolder = "${sanitizedVendorName}-${principal.vendorId}"
            trace.step("Vendor folder resolved", mapOf("vendorFolder" to vendorFolder))

            trace.step("Processing multipart log upload")
            val multipart = call.receiveMultipart()
            var savedFileName: String? = null
            var systemName: String? = null
            var fileBytes: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "system_name") {
                            systemName = part.value
                        }
                    }
                    is PartData.FileItem -> {
                        fileBytes = part.streamProvider().readBytes()
                    }
                    else -> {}
                }
                part.dispose()
            }

            trace.step("Multipart parsed", mapOf(
                "hasFile" to (fileBytes != null).toString(),
                "fileSize" to (fileBytes?.size?.toString() ?: "0"),
                "systemName" to (systemName ?: "null")
            ))

            if (fileBytes != null) {
                // Determine system name from form field or fallback to role
                val sysName = systemName ?: when (principal.role) {
                    "MANAGER" -> "manager"
                    "CASHIER" -> "cashier"
                    "DELIVERY" -> "delivery"
                    else -> "app"
                }

                // Create logs directory: logs/vendors/<vendorname-vendorid>/
                val logsDir = File(File("logs", "vendors"), vendorFolder)
                if (!logsDir.exists()) logsDir.mkdirs()

                // Generate filename: <systemname>-logs-<dd-MM-yyyy>.log
                val dateStr = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
                )
                val fileName = "${sysName}-logs-${dateStr}.log"
                val file = File(logsDir, fileName)

                trace.step("Writing log file", mapOf(
                    "fileName" to fileName,
                    "directory" to logsDir.path,
                    "sysName" to sysName,
                    "fileSize" to fileBytes!!.size.toString()
                ))

                // Append to existing file if same day, otherwise create new
                FileOutputStream(file, true).buffered().use { output ->
                    if (file.length() > 0) {
                        output.write("\n--- Upload at ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} ---\n".toByteArray())
                    }
                    output.write(fileBytes!!)
                }

                trace.step("Log file written", mapOf(
                    "fileName" to fileName,
                    "totalFileSize" to file.length().toString()
                ))

                savedFileName = fileName
            }

            if (savedFileName != null) {
                trace.step("Log upload completed", mapOf("filename" to savedFileName!!))
                call.respond(
                    HttpStatusCode.OK,
                    LogUploadResponse(
                        message = "Log uploaded successfully",
                        filename = savedFileName!!,
                    )
                )
            } else {
                trace.step("Log upload failed - no log file in request")
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "No log file uploaded")
                )
            }
        }
    }
}
