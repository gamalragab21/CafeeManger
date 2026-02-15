package net.marllex.cafeemanger.backend.domain.service

import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.datetime.Instant
import net.marllex.cafeemanger.backend.data.database.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExportService {

    data class ExportData(
        val vendorName: String,
        val fromDate: Long,
        val toDate: Long,
        val orders: List<OrderExportData>,
        val summary: ExportSummary
    )

    data class OrderExportData(
        val orderId: String,
        val orderNumber: String,
        val date: String,
        val time: String,
        val channel: String,
        val status: String,
        val paymentMethod: String,
        val customerName: String?,
        val customerPhone: String?,
        val items: List<OrderItemExportData>,
        val subtotal: Double,
        val tax: Double,
        val deliveryFee: Double,
        val total: Double,
        val cashierName: String?,
        val deliveryPersonName: String?
    )

    data class OrderItemExportData(
        val itemName: String,
        val quantity: Int,
        val price: Double,
        val total: Double
    )

    data class ExportSummary(
        val totalOrders: Int,
        val totalRevenue: Double,
        val totalTax: Double,
        val totalDeliveryFees: Double,
        val ordersByChannel: Map<String, Int>,
        val ordersByPaymentMethod: Map<String, Int>,
        val ordersByStatus: Map<String, Int>
    )

//    fun getExportData(vendorId: UUID, fromDate: Long, toDate: Long): ExportData {
//        return transaction {
//            // Get vendor info
//            val vendor = VendorsTable.select { VendorsTable.id eq vendorId }
//                .singleOrNull()
//                ?: throw IllegalArgumentException("Vendor not found")
//
//            val vendorName = vendor[VendorsTable.name]
//
//            // Get orders in date range
//            val fromInstant = Instant.fromEpochMilliseconds(fromDate)
//            val toInstant = Instant.fromEpochMilliseconds(toDate)
//
//            val orders = OrdersTable
//                .leftJoin(UsersTable, { OrdersTable.cashierId }, { UsersTable.id })
//                .select {
//                    (OrdersTable.vendorId eq vendorId) and
//                            (OrdersTable.createdAt greaterEq fromInstant) and
//                            (OrdersTable.createdAt lessEq toInstant)
//                }
//                .orderBy(OrdersTable.createdAt, SortOrder.DESC)
//                .map { orderRow ->
//                    val orderId = orderRow[OrdersTable.id]
//                    val createdAt = orderRow[OrdersTable.createdAt].toEpochMilliseconds()
//                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
//                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
//
//                    // Get order items
//                    val items = OrderItemsTable
//                        .select { OrderItemsTable.orderId eq orderId }
//                        .map { itemRow ->
//                            OrderItemExportData(
//                                itemName = itemRow[OrderItemsTable.itemNameSnapshot],
//                                quantity = itemRow[OrderItemsTable.quantity],
//                                price = itemRow[OrderItemsTable.itemPriceSnapshot].toDouble(),
//                                total = itemRow[OrderItemsTable.itemPriceSnapshot].toDouble() * itemRow[OrderItemsTable.quantity]
//                            )
//                        }
//
//                    // Get delivery person name if exists
//                    val deliveryPersonName = orderRow[OrdersTable.deliveryUserId]?.let { deliveryId ->
//                        UsersTable.select { UsersTable.id eq deliveryId }
//                            .singleOrNull()
//                            ?.get(UsersTable.name)
//                    }
//
//                    OrderExportData(
//                        orderId = orderId.toString(),
//                        orderNumber = orderId.toString().takeLast(8).uppercase(),
//                        date = dateFormat.format(Date(createdAt)),
//                        time = timeFormat.format(Date(createdAt)),
//                        channel = orderRow[OrdersTable.channel],
//                        status = orderRow[OrdersTable.status],
//                        paymentMethod = orderRow[OrdersTable.paymentMethod],
//                        customerName = orderRow[OrdersTable.clientName],
//                        customerPhone = orderRow[OrdersTable.clientPhone],
//                        items = items,
//                        subtotal = orderRow[OrdersTable.subtotal].toDouble(),
//                        tax = orderRow[OrdersTable.tax].toDouble(),
//                        deliveryFee = orderRow[OrdersTable.deliveryFee]?.toDouble() ?: 0.0,
//                        total = orderRow[OrdersTable.total].toDouble(),
//                        cashierName = orderRow.getOrNull(UsersTable.name),
//                        deliveryPersonName = deliveryPersonName
//                    )
//                }
//
//            // Calculate summary
//            val summary = ExportSummary(
//                totalOrders = orders.size,
//                totalRevenue = orders.sumOf { it.total },
//                totalTax = orders.sumOf { it.tax },
//                totalDeliveryFees = orders.sumOf { it.deliveryFee },
//                ordersByChannel = orders.groupBy { it.channel }.mapValues { it.value.size },
//                ordersByPaymentMethod = orders.groupBy { it.paymentMethod }.mapValues { it.value.size },
//                ordersByStatus = orders.groupBy { it.status }.mapValues { it.value.size }
//            )
//
//            ExportData(
//                vendorName = vendorName,
//                fromDate = fromDate,
//                toDate = toDate,
//                orders = orders,
//                summary = summary
//            )
//        }
//    }

    fun generatePDF(data: ExportData): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Title
        val title = Paragraph("${data.vendorName} - Sales Report")
            .setFontSize(20f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
        document.add(title)

        // Date Range
        val dateRange = Paragraph("Period: ${dateFormat.format(Date(data.fromDate))} to ${dateFormat.format(Date(data.toDate))}")
            .setFontSize(12f)
            .setTextAlignment(TextAlignment.CENTER)
        document.add(dateRange)

        document.add(Paragraph("\n"))

        // Summary Section
        val summaryTitle = Paragraph("Summary")
            .setFontSize(16f)
            .setBold()
        document.add(summaryTitle)

        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .useAllAvailableWidth()

        summaryTable.addCell(createCell("Total Orders:", true))
        summaryTable.addCell(createCell(data.summary.totalOrders.toString(), false))

        summaryTable.addCell(createCell("Total Revenue:", true))
        summaryTable.addCell(createCell(String.format("%.2f EGP", data.summary.totalRevenue), false))

        summaryTable.addCell(createCell("Total Delivery Fees:", true))
        summaryTable.addCell(createCell(String.format("%.2f EGP", data.summary.totalDeliveryFees), false))

        document.add(summaryTable)
        document.add(Paragraph("\n"))

        // Orders by Channel
        val channelTitle = Paragraph("Orders by Channel")
            .setFontSize(14f)
            .setBold()
        document.add(channelTitle)

        val channelTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .useAllAvailableWidth()

        data.summary.ordersByChannel.forEach { (channel, count) ->
            channelTable.addCell(createCell(channel, false))
            channelTable.addCell(createCell(count.toString(), false))
        }
        document.add(channelTable)
        document.add(Paragraph("\n"))

        // Orders by Payment Method
        val paymentTitle = Paragraph("Orders by Payment Method")
            .setFontSize(14f)
            .setBold()
        document.add(paymentTitle)

        val paymentTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .useAllAvailableWidth()

        data.summary.ordersByPaymentMethod.forEach { (method, count) ->
            paymentTable.addCell(createCell(method, false))
            paymentTable.addCell(createCell(count.toString(), false))
        }
        document.add(paymentTable)
        document.add(Paragraph("\n"))

        // Detailed Orders
        val ordersTitle = Paragraph("Detailed Orders")
            .setFontSize(16f)
            .setBold()
        document.add(ordersTitle)

        val ordersTable = Table(UnitValue.createPercentArray(floatArrayOf(10f, 15f, 15f, 15f, 15f, 15f, 15f)))
            .useAllAvailableWidth()

        // Header
        ordersTable.addHeaderCell(createHeaderCell("Order #"))
        ordersTable.addHeaderCell(createHeaderCell("Date"))
        ordersTable.addHeaderCell(createHeaderCell("Channel"))
        ordersTable.addHeaderCell(createHeaderCell("Payment"))
        ordersTable.addHeaderCell(createHeaderCell("Status"))
        ordersTable.addHeaderCell(createHeaderCell("Customer"))
        ordersTable.addHeaderCell(createHeaderCell("Total"))

        data.orders.forEach { order ->
            ordersTable.addCell(createCell(order.orderNumber, false))
            ordersTable.addCell(createCell("${order.date}\n${order.time}", false))
            ordersTable.addCell(createCell(order.channel, false))
            ordersTable.addCell(createCell(order.paymentMethod, false))
            ordersTable.addCell(createCell(order.status, false))
            ordersTable.addCell(createCell(order.customerName ?: order.customerPhone ?: "N/A", false))
            ordersTable.addCell(createCell(String.format("%.2f", order.total), false))
        }

        document.add(ordersTable)

        document.close()
        return outputStream.toByteArray()
    }

    fun generateExcel(data: ExportData): ByteArray {
        val workbook: Workbook = XSSFWorkbook()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Create Summary Sheet
        val summarySheet = workbook.createSheet("Summary")
        var rowNum = 0

        // Title
        var row = summarySheet.createRow(rowNum++)
        var cell = row.createCell(0)
        cell.setCellValue("${data.vendorName} - Sales Report")
        cell.cellStyle = createHeaderStyle(workbook)

        // Date Range
        row = summarySheet.createRow(rowNum++)
        cell = row.createCell(0)
        cell.setCellValue("Period: ${dateFormat.format(Date(data.fromDate))} to ${dateFormat.format(Date(data.toDate))}")

        rowNum++ // Empty row

        // Summary Data
        row = summarySheet.createRow(rowNum++)
        row.createCell(0).setCellValue("Total Orders")
        row.createCell(1).setCellValue(data.summary.totalOrders.toDouble())

        row = summarySheet.createRow(rowNum++)
        row.createCell(0).setCellValue("Total Revenue (EGP)")
        row.createCell(1).setCellValue(data.summary.totalRevenue)

        row = summarySheet.createRow(rowNum++)
        row.createCell(0).setCellValue("Total Delivery Fees (EGP)")
        row.createCell(1).setCellValue(data.summary.totalDeliveryFees)

        rowNum++ // Empty row

        // Orders by Channel
        row = summarySheet.createRow(rowNum++)
        cell = row.createCell(0)
        cell.setCellValue("Orders by Channel")
        cell.cellStyle = createHeaderStyle(workbook)

        data.summary.ordersByChannel.forEach { (channel, count) ->
            row = summarySheet.createRow(rowNum++)
            row.createCell(0).setCellValue(channel)
            row.createCell(1).setCellValue(count.toDouble())
        }

        rowNum++ // Empty row

        // Orders by Payment Method
        row = summarySheet.createRow(rowNum++)
        cell = row.createCell(0)
        cell.setCellValue("Orders by Payment Method")
        cell.cellStyle = createHeaderStyle(workbook)

        data.summary.ordersByPaymentMethod.forEach { (method, count) ->
            row = summarySheet.createRow(rowNum++)
            row.createCell(0).setCellValue(method)
            row.createCell(1).setCellValue(count.toDouble())
        }

        // Auto-size columns
        summarySheet.autoSizeColumn(0)
        summarySheet.autoSizeColumn(1)

        // Create Orders Sheet
        val ordersSheet = workbook.createSheet("Orders")
        rowNum = 0

        // Header
        row = ordersSheet.createRow(rowNum++)
        val headerStyle = createHeaderStyle(workbook)
        val headers = listOf("Order #", "Date", "Time", "Channel", "Payment Method", "Status", 
                            "Customer Name", "Customer Phone", "Subtotal", "Delivery Fees", "Total", 
                            "Cashier", "Delivery Person")
        headers.forEachIndexed { index, header ->
            cell = row.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Data
        data.orders.forEach { order ->
            row = ordersSheet.createRow(rowNum++)
            row.createCell(0).setCellValue(order.orderNumber)
            row.createCell(1).setCellValue(order.date)
            row.createCell(2).setCellValue(order.time)
            row.createCell(3).setCellValue(order.channel)
            row.createCell(4).setCellValue(order.paymentMethod)
            row.createCell(5).setCellValue(order.status)
            row.createCell(6).setCellValue(order.customerName ?: "")
            row.createCell(7).setCellValue(order.customerPhone ?: "")
            row.createCell(8).setCellValue(order.subtotal)
            row.createCell(9).setCellValue(order.deliveryFee)
            row.createCell(10).setCellValue(order.total)
            row.createCell(11).setCellValue(order.cashierName ?: "")
            row.createCell(12).setCellValue(order.deliveryPersonName ?: "")
        }

        // Auto-size columns
        for (i in 0 until headers.size) {
            ordersSheet.autoSizeColumn(i)
        }

        // Create Order Items Sheet
        val itemsSheet = workbook.createSheet("Order Items")
        rowNum = 0

        // Header
        row = itemsSheet.createRow(rowNum++)
        val itemHeaders = listOf("Order #", "Item Name", "Quantity", "Price", "Total")
        itemHeaders.forEachIndexed { index, header ->
            cell = row.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Data
        data.orders.forEach { order ->
            order.items.forEach { item ->
                row = itemsSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(order.orderNumber)
                row.createCell(1).setCellValue(item.itemName)
                row.createCell(2).setCellValue(item.quantity.toDouble())
                row.createCell(3).setCellValue(item.price)
                row.createCell(4).setCellValue(item.total)
            }
        }

        // Auto-size columns
        for (i in 0 until itemHeaders.size) {
            itemsSheet.autoSizeColumn(i)
        }

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()
        return outputStream.toByteArray()
    }

    private fun createCell(content: String, isBold: Boolean): Cell {
        val cell = Cell().add(Paragraph(content))
        if (isBold) {
            cell.setBold()
        }
        return cell
    }

    private fun createHeaderCell(content: String): Cell {
        return Cell()
            .add(Paragraph(content).setBold())
            .setBackgroundColor(DeviceRgb(200, 200, 200))
            .setTextAlignment(TextAlignment.CENTER)
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 12
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }
}
