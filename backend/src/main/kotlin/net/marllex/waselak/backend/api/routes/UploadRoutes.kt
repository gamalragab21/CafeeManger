package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.header
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
import java.net.URLEncoder
import java.util.UUID

@Serializable
data class UploadResponse(
    val url: String,
)

fun Route.uploadRoutes() {
    route("/api/v1/upload") {
        post {
            val trace = call.routeTrace()
            trace.step("File upload started")
            val principal = requireRole("MANAGER", "CASHIER", "DELIVERY")
            trace.step("Authenticated", mapOf("vendorId" to principal.vendorId, "role" to principal.role))

            // Get vendor name for folder organization
            trace.step("Looking up vendor name")
            val vendorName = transaction {
                VendorsTable.selectAll()
                    .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                    .firstOrNull()?.get(VendorsTable.name)
            } ?: "unknown"

            // Create sanitized folder name: vendorname-vendorid (ASCII-only for URL safety)
            val sanitizedVendorName = vendorName.replace(Regex("[^a-zA-Z0-9-_ ]"), "").trim().replace(" ", "-").take(50)
            val vendorFolder = if (sanitizedVendorName.isBlank()) principal.vendorId else "${sanitizedVendorName}-${principal.vendorId}"
            trace.step("Vendor folder resolved", mapOf("vendorFolder" to vendorFolder))

            // Build base URL from request headers (works with ngrok / reverse proxies)
            val host = call.request.header("Host") ?: "localhost:8080"
            val scheme = call.request.header("X-Forwarded-Proto") ?: "http"
            val baseUrl = "$scheme://$host"

            trace.step("Processing multipart upload")
            val multipart = call.receiveMultipart()
            var uploadedFileName: String? = null
            var oldUrl: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val originalFileName = part.originalFileName ?: "image.jpg"
                        val extension = originalFileName.substringAfterLast('.', "jpg")
                            .lowercase()
                            .takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif") }
                            ?: "jpg"

                        trace.step("File part received", mapOf(
                            "filename" to originalFileName,
                            "contentType" to (part.contentType?.toString() ?: "null"),
                            "extension" to extension
                        ))

                        // Create uploads directory structure: uploads/<vendorname-vendorid>/
                        val uploadsDir = File("uploads", vendorFolder)
                        if (!uploadsDir.exists()) uploadsDir.mkdirs()

                        // Generate unique filename
                        val fileName = "${UUID.randomUUID()}.$extension"
                        val file = File(uploadsDir, fileName)

                        // Write file
                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }

                        trace.step("File written to disk", mapOf(
                            "savedAs" to fileName,
                            "size" to file.length().toString()
                        ))

                        uploadedFileName = fileName
                    }
                    is PartData.FormItem -> {
                        // Accept old_url field to delete the previous image
                        if (part.name == "old_url") {
                            oldUrl = part.value
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            // Delete old file if old_url was provided
            if (oldUrl != null) {
                trace.step("Deleting old file", mapOf("oldUrl" to oldUrl!!))
                deleteUploadedFile(oldUrl!!)
            }

            if (uploadedFileName != null) {
                val encodedFolder = URLEncoder.encode(vendorFolder, "UTF-8").replace("+", "%20")
                val fullUrl = "$baseUrl/uploads/$encodedFolder/$uploadedFileName"
                trace.step("File upload completed", mapOf("url" to fullUrl))
                call.respond(HttpStatusCode.OK, UploadResponse(url = fullUrl))
            } else {
                trace.step("File upload failed - no file in request")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
            }
        }
    }
}

/**
 * Rewrites a stored upload URL to use the current host.
 * Handles stale ngrok/proxy URLs by extracting the filename and rebuilding with current host.
 * URL-encodes path segments to handle non-ASCII (e.g. Arabic) folder names safely.
 * Returns null if the input URL is null or blank.
 */
fun rewriteUploadUrl(storedUrl: String?, currentHost: String, currentScheme: String): String? {
    if (storedUrl.isNullOrBlank()) return null
    if (!storedUrl.contains("/uploads/")) return storedUrl
    val relativePath = storedUrl.substringAfterLast("/uploads/")
    if (relativePath.isBlank()) return storedUrl
    // URL-encode each path segment to handle Arabic/non-ASCII folder names
    val encodedPath = relativePath.split("/").joinToString("/") { segment ->
        // Don't re-encode segments that are already encoded (contain %)
        if (segment.contains("%")) segment
        else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }
    return "$currentScheme://$currentHost/uploads/$encodedPath"
}

/**
 * Safely deletes an uploaded file from disk given its full URL.
 * Extracts the filename from the URL path and removes it from the uploads directory.
 * Only deletes files within the uploads/ directory to prevent path traversal.
 */
fun deleteUploadedFile(fileUrl: String) {
    try {
        // Extract relative path from URL (e.g. "http://host/uploads/vendor-folder/abc-123.jpg" -> "vendor-folder/abc-123.jpg")
        val relativePath = fileUrl.substringAfterLast("/uploads/", "")
        if (relativePath.isBlank()) return

        // Prevent path traversal attacks
        if (relativePath.contains("..")) return

        val file = File("uploads", relativePath)
        // Ensure file is within uploads directory
        if (file.exists() && file.canonicalPath.startsWith(File("uploads").canonicalPath)) {
            file.delete()
        }
    } catch (_: Exception) {
        // Don't fail the request if file deletion fails
    }
}
