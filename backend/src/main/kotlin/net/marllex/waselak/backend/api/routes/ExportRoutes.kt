package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.domain.service.ExportService
import net.marllex.waselak.backend.domain.service.PlanService
import org.koin.java.KoinJavaComponent
import java.text.SimpleDateFormat
import java.util.*
import kotlin.getValue

fun Route.exportRoutes() {
    val exportService by KoinJavaComponent.inject<ExportService>(clazz = ExportService::class.java)
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)
    route("/api/v1/export") {

        // Export orders as PDF
        get("/orders/pdf") {
            val trace = call.routeTrace()
            trace.step("Export orders PDF started")
            val principal = requireRole("MANAGER")
            val vendorId = UUID.fromString(principal.vendorId)
            planService.checkFeature(vendorId, "EXPORT")

            val fromDate = call.parameters["from"]?.toLongOrNull()
                ?: throw IllegalArgumentException("'from' parameter is required (timestamp in milliseconds)")
            val toDate = call.parameters["to"]?.toLongOrNull()
                ?: throw IllegalArgumentException("'to' parameter is required (timestamp in milliseconds)")
            trace.step("Export parameters", mapOf("exportType" to "orders", "format" to "PDF", "fromDate" to fromDate.toString(), "toDate" to toDate.toString()))

            try {
                val data = exportService.getExportData(vendorId, fromDate, toDate)
                trace.step("Export data fetched", mapOf("recordCount" to data.orders.size.toString()))
                val pdfBytes = exportService.generatePDF(data)
                trace.step("PDF generated", mapOf("fileSizeBytes" to pdfBytes.size.toString()))

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val fromDateStr = dateFormat.format(Date(fromDate))
                val toDateStr = dateFormat.format(Date(toDate))
                val filename = "sales_report_${fromDateStr}_to_${toDateStr}.pdf"

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        filename
                    ).toString()
                )

                trace.step("Export orders PDF completed")
                call.respondBytes(
                    bytes = pdfBytes,
                    contentType = ContentType.Application.Pdf
                )
            } catch (e: Exception) {
                trace.step("Export orders PDF failed", mapOf("error" to (e.message ?: "null")))
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to generate PDF: ${e.message}")
                )
            }
        }

        // Export orders as Excel
        get("/orders/excel") {
            val trace = call.routeTrace()
            trace.step("Export orders Excel started")
            val principal = requireRole("MANAGER")
            val vendorId = UUID.fromString(principal.vendorId)
            planService.checkFeature(vendorId, "EXPORT")

            val fromDate = call.parameters["from"]?.toLongOrNull()
                ?: throw IllegalArgumentException("'from' parameter is required (timestamp in milliseconds)")
            val toDate = call.parameters["to"]?.toLongOrNull()
                ?: throw IllegalArgumentException("'to' parameter is required (timestamp in milliseconds)")
            trace.step("Export parameters", mapOf("exportType" to "orders", "format" to "Excel", "fromDate" to fromDate.toString(), "toDate" to toDate.toString()))

            try {
                val data = exportService.getExportData(vendorId, fromDate, toDate)
                trace.step("Export data fetched", mapOf("recordCount" to data.orders.size.toString()))
                val excelBytes = exportService.generateExcel(data)
                trace.step("Excel generated", mapOf("fileSizeBytes" to excelBytes.size.toString()))

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val fromDateStr = dateFormat.format(Date(fromDate))
                val toDateStr = dateFormat.format(Date(toDate))
                val filename = "sales_report_${fromDateStr}_to_${toDateStr}.xlsx"

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        filename
                    ).toString()
                )

                trace.step("Export orders Excel completed")
                call.respondBytes(
                    bytes = excelBytes,
                    contentType = ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                )
            } catch (e: Exception) {
                trace.step("Export orders Excel failed", mapOf("error" to (e.message ?: "null")))
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to generate Excel: ${e.message}")
                )
            }
        }

        // Get export preview (JSON)
        get("/orders/preview") {
            val trace = call.routeTrace()
            trace.step("Export orders preview started")
            val principal = requireRole("MANAGER")
            val vendorId = UUID.fromString(principal.vendorId)
            planService.checkFeature(vendorId, "EXPORT")

            val fromDate = call.parameters["from"]?.toLongOrNull()
                ?: throw IllegalArgumentException("'from' parameter is required (timestamp in milliseconds)")
            val toDate = call.parameters["to"]?.toLongOrNull()
                ?: throw IllegalArgumentException("'to' parameter is required (timestamp in milliseconds)")
            trace.step("Export parameters", mapOf("exportType" to "orders", "format" to "preview", "fromDate" to fromDate.toString(), "toDate" to toDate.toString()))

            try {
                val data = exportService.getExportData(vendorId, fromDate, toDate)
                trace.step("Export preview data fetched", mapOf("recordCount" to data.orders.size.toString()))

                trace.step("Export orders preview completed")
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "vendor_name" to data.vendorName,
                        "from_date" to data.fromDate,
                        "to_date" to data.toDate,
                        "summary" to mapOf(
                            "total_orders" to data.summary.totalOrders,
                            "total_revenue" to data.summary.totalRevenue,
                            "total_delivery_fees" to data.summary.totalDeliveryFees,
                            "orders_by_channel" to data.summary.ordersByChannel,
                            "orders_by_payment_method" to data.summary.ordersByPaymentMethod,
                            "orders_by_status" to data.summary.ordersByStatus
                        ),
                        "orders_count" to data.orders.size
                    )
                )
            } catch (e: Exception) {
                trace.step("Export orders preview failed", mapOf("error" to (e.message ?: "null")))
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get preview: ${e.message}")
                )
            }
        }
    }
}
