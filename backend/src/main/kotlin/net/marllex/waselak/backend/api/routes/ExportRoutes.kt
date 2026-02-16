package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.domain.service.ExportService
//import net.marllex.waselak.backend.plugins.requireRole
import org.koin.java.KoinJavaComponent
import java.text.SimpleDateFormat
import java.util.*
import kotlin.getValue

fun Route.exportRoutes() {
    val exportService by KoinJavaComponent.inject<ExportService>(clazz = ExportService::class.java)

    route("/api/v1/export") {

//        // Export orders as PDF
//        get("/orders/pdf") {
//            val principal = requireRole("MANAGER")
//            val vendorId = UUID.fromString(principal.vendorId)
//
//            // Get date range parameters
//            val fromDate = call.parameters["from"]?.toLongOrNull()
//                ?: throw IllegalArgumentException("'from' parameter is required (timestamp in milliseconds)")
//            val toDate = call.parameters["to"]?.toLongOrNull()
//                ?: throw IllegalArgumentException("'to' parameter is required (timestamp in milliseconds)")
//
//            try {
//                // Get export data
//                val data = exportService.getExportData(vendorId, fromDate, toDate)
//
//                // Generate PDF
//                val pdfBytes = exportService.generatePDF(data)
//
//                // Format filename with date range
//                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
//                val fromDateStr = dateFormat.format(Date(fromDate))
//                val toDateStr = dateFormat.format(Date(toDate))
//                val filename = "sales_report_${fromDateStr}_to_${toDateStr}.pdf"
//
//                // Set response headers
//                call.response.header(
//                    HttpHeaders.ContentDisposition,
//                    ContentDisposition.Attachment.withParameter(
//                        ContentDisposition.Parameters.FileName,
//                        filename
//                    ).toString()
//                )
//
//                call.respondBytes(
//                    bytes = pdfBytes,
//                    contentType = ContentType.Application.Pdf
//                )
//            } catch (e: Exception) {
//                call.respond(
//                    HttpStatusCode.InternalServerError,
//                    mapOf("error" to "Failed to generate PDF: ${e.message}")
//                )
//            }
//        }
//
//        // Export orders as Excel
//        get("/orders/excel") {
//            val principal = requireRole("MANAGER")
//            val vendorId = UUID.fromString(principal.vendorId)
//
//            // Get date range parameters
//            val fromDate = call.parameters["from"]?.toLongOrNull()
//                ?: throw IllegalArgumentException("'from' parameter is required (timestamp in milliseconds)")
//            val toDate = call.parameters["to"]?.toLongOrNull()
//                ?: throw IllegalArgumentException("'to' parameter is required (timestamp in milliseconds)")
//
//            try {
//                // Get export data
//                val data = exportService.getExportData(vendorId, fromDate, toDate)
//
//                // Generate Excel
//                val excelBytes = exportService.generateExcel(data)
//
//                // Format filename with date range
//                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
//                val fromDateStr = dateFormat.format(Date(fromDate))
//                val toDateStr = dateFormat.format(Date(toDate))
//                val filename = "sales_report_${fromDateStr}_to_${toDateStr}.xlsx"
//
//                // Set response headers
//                call.response.header(
//                    HttpHeaders.ContentDisposition,
//                    ContentDisposition.Attachment.withParameter(
//                        ContentDisposition.Parameters.FileName,
//                        filename
//                    ).toString()
//                )
//
//                call.respondBytes(
//                    bytes = excelBytes,
//                    contentType = ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
//                )
//            } catch (e: Exception) {
//                call.respond(
//                    HttpStatusCode.InternalServerError,
//                    mapOf("error" to "Failed to generate Excel: ${e.message}")
//                )
//            }
//        }
//
//        // Get export preview (JSON) - useful for UI preview before downloading
//        get("/orders/preview") {
//            val principal = requireRole("MANAGER")
//            val vendorId = UUID.fromString(principal.vendorId)
//
//            val fromDate = call.parameters["from"]?.toLongOrNull()
//                ?: throw IllegalArgumentException("'from' parameter is required (timestamp in milliseconds)")
//            val toDate = call.parameters["to"]?.toLongOrNull()
//                ?: throw IllegalArgumentException("'to' parameter is required (timestamp in milliseconds)")
//
//            try {
//                val data = exportService.getExportData(vendorId, fromDate, toDate)
//
//                call.respond(
//                    HttpStatusCode.OK,
//                    mapOf(
//                        "vendor_name" to data.vendorName,
//                        "from_date" to data.fromDate,
//                        "to_date" to data.toDate,
//                        "summary" to mapOf(
//                            "total_orders" to data.summary.totalOrders,
//                            "total_revenue" to data.summary.totalRevenue,
//                            "total_delivery_fees" to data.summary.totalDeliveryFees,
//                            "orders_by_channel" to data.summary.ordersByChannel,
//                            "orders_by_payment_method" to data.summary.ordersByPaymentMethod,
//                            "orders_by_status" to data.summary.ordersByStatus
//                        ),
//                        "orders_count" to data.orders.size
//                    )
//                )
//            } catch (e: Exception) {
//                call.respond(
//                    HttpStatusCode.InternalServerError,
//                    mapOf("error" to "Failed to get preview: ${e.message}")
//                )
//            }
//        }
    }
}
