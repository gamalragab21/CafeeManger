package net.marllex.waselak.backend.domain.service

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
import net.marllex.waselak.backend.data.database.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates full-merchant export files (PDF + Excel) covering every analytics section
 * the dashboard shows — not just orders. The flow:
 *
 *  1. `getExportData()` collects:
 *      - Vendor info + date range (context)
 *      - Orders and order items (line-item detail, unchanged from previous implementation)
 *      - Every section returned by [AnalyticsQueryService] — revenue, cashiers, products,
 *        customers, alerts, stock, loyalty, discounts, offers, suppliers, staff costs,
 *        delivery performance. Each section is wrapped in `runCatching` so a single
 *        failing query (e.g. plan-gated feature off) doesn't kill the whole export.
 *
 *  2. `generateExcel()` emits one sheet per section — so a merchant can click a tab
 *     to jump to "Top Customers", another for "Stock Low", etc.
 *
 *  3. `generatePDF()` emits a page-friendly summary: cover page, summary KPIs, then
 *     one titled sub-section per analytics category, capping row counts to keep the
 *     file a reasonable size (big data belongs in Excel).
 *
 * Any section that failed to fetch is still noted in a `_Errors` sheet / final PDF
 * page so the merchant can see what's missing and why, instead of silently dropping
 * data.
 */
class ExportService(
    private val analyticsQueryService: AnalyticsQueryService,
) {

    // ─── Public data model ──────────────────────────────────────────────────

    data class ExportData(
        val vendorName: String,
        val fromDate: Long,
        val toDate: Long,
        val orders: List<OrderExportData>,
        val summary: ExportSummary,
        /** One entry per analytics category. Order preserved so PDF/Excel output is deterministic. */
        val sections: List<ExportSection>,
        /** Sections whose query threw — still shown to merchant so they know what's missing. */
        val failedSections: List<FailedSection>,
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
        val deliveryFee: Double,
        val total: Double,
        val cashierName: String?,
        val deliveryPersonName: String?,
    )

    data class OrderItemExportData(
        val itemName: String,
        val quantity: Int,
        val price: Double,
        val total: Double,
    )

    data class ExportSummary(
        val totalOrders: Int,
        val totalRevenue: Double,
        val totalDeliveryFees: Double,
        val ordersByChannel: Map<String, Int>,
        val ordersByPaymentMethod: Map<String, Int>,
        val ordersByStatus: Map<String, Int>,
    )

    /**
     * A generic tabular section of exported data. `sheetName` is used as the Excel
     * tab name (must be ≤ 31 chars and not contain `\/?*[]:`). `title` is the
     * human-readable heading used on the PDF.
     */
    data class ExportSection(
        val sheetName: String,
        val title: String,
        val columns: List<String>,
        val rows: List<List<String>>,
    )

    data class FailedSection(val title: String, val error: String)

    // ─── Data collection ────────────────────────────────────────────────────

    fun getExportData(vendorId: UUID, fromDate: Long, toDate: Long): ExportData {
        val fromInstant = Instant.fromEpochMilliseconds(fromDate)
        val toInstant = Instant.fromEpochMilliseconds(toDate)

        val (vendorName, orders, summary) = transaction {
            val vendor = VendorsTable.selectAll().where { VendorsTable.id eq vendorId }
                .singleOrNull()
                ?: throw IllegalArgumentException("Vendor not found")
            val vendorName = vendor[VendorsTable.name]
            val userNames = UsersTable.selectAll().where {
                UsersTable.vendorId eq vendorId
            }.associate { it[UsersTable.id] to it[UsersTable.name] }

            val orderRows = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                    (OrdersTable.createdAt greaterEq fromInstant) and
                    (OrdersTable.createdAt lessEq toInstant)
            }.orderBy(OrdersTable.createdAt, SortOrder.DESC).toList()

            val allOrderIds = orderRows.map { it[OrdersTable.id] }
            val allOrderItems = if (allOrderIds.isNotEmpty()) {
                OrderItemsTable.selectAll().where {
                    OrderItemsTable.orderId inList allOrderIds
                }.toList().groupBy { it[OrderItemsTable.orderId] }
            } else emptyMap()

            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            val orders = orderRows.map { orderRow ->
                val orderId = orderRow[OrdersTable.id]
                val createdAt = orderRow[OrdersTable.createdAt].toEpochMilliseconds()
                val items = (allOrderItems[orderId] ?: emptyList()).map { itemRow ->
                    OrderItemExportData(
                        itemName = itemRow[OrderItemsTable.itemNameSnapshot],
                        quantity = itemRow[OrderItemsTable.quantity],
                        price = itemRow[OrderItemsTable.itemPriceSnapshot].toDouble(),
                        total = itemRow[OrderItemsTable.itemPriceSnapshot].toDouble() * itemRow[OrderItemsTable.quantity],
                    )
                }
                val deliveryName = orderRow[OrdersTable.deliveryUserId]?.let { userNames[it] }
                OrderExportData(
                    orderId = orderId.toString(),
                    orderNumber = orderId.toString().takeLast(8).uppercase(),
                    date = dateFmt.format(Date(createdAt)),
                    time = timeFmt.format(Date(createdAt)),
                    channel = orderRow[OrdersTable.channel],
                    status = orderRow[OrdersTable.status],
                    paymentMethod = orderRow[OrdersTable.paymentMethod],
                    customerName = orderRow[OrdersTable.clientName],
                    customerPhone = orderRow[OrdersTable.clientPhone],
                    items = items,
                    subtotal = orderRow[OrdersTable.subtotal].toDouble(),
                    deliveryFee = orderRow[OrdersTable.deliveryFee].toDouble() + orderRow[OrdersTable.tax].toDouble(),
                    total = orderRow[OrdersTable.total].toDouble(),
                    cashierName = userNames[orderRow[OrdersTable.cashierId]],
                    deliveryPersonName = deliveryName,
                )
            }

            val summary = ExportSummary(
                totalOrders = orders.size,
                totalRevenue = orders.sumOf { it.total },
                totalDeliveryFees = orders.sumOf { it.deliveryFee },
                ordersByChannel = orders.groupBy { it.channel }.mapValues { it.value.size },
                ordersByPaymentMethod = orders.groupBy { it.paymentMethod }.mapValues { it.value.size },
                ordersByStatus = orders.groupBy { it.status }.mapValues { it.value.size },
            )

            Triple(vendorName, orders, summary)
        }

        // Gather each analytics section in its own try-block so a single failing query
        // (e.g. a feature gated off by plan, a permission slip, or a DB hiccup) doesn't
        // sink the whole export. We collect errors for the merchant to see.
        val sections = mutableListOf<ExportSection>()
        val failed = mutableListOf<FailedSection>()
        fun section(title: String, block: () -> List<ExportSection>) {
            try {
                sections += block()
            } catch (e: Throwable) {
                failed += FailedSection(title, e.message ?: e::class.simpleName ?: "unknown")
            }
        }

        section("Revenue & Profit") { revenueSections(vendorId, fromInstant, toInstant) }
        section("Orders Intelligence") { ordersIntelligenceSections(vendorId, fromInstant, toInstant) }
        section("Peak Times") { peakTimesSections(vendorId, fromInstant, toInstant) }
        section("Cashier Performance") { cashierSections(vendorId, fromInstant, toInstant) }
        section("Delivery Performance") { deliverySections(vendorId, fromInstant, toInstant) }
        section("Product Intelligence") { productSections(vendorId, fromInstant, toInstant) }
        section("Customer Intelligence") { customerSections(vendorId, fromInstant, toInstant) }
        section("Alerts") { alertsSections(vendorId, fromInstant, toInstant) }
        section("Stock Overview") { stockSections(vendorId) }
        section("Offers") { offersSections(vendorId, fromInstant, toInstant) }
        section("Discounts") { discountsSections(vendorId, fromInstant, toInstant) }
        section("Loyalty") { loyaltySections(vendorId, fromInstant, toInstant) }
        section("Staff Costs") { staffCostSections(vendorId, fromInstant, toInstant) }
        section("Suppliers") { supplierSections(vendorId, fromInstant, toInstant) }

        return ExportData(
            vendorName = vendorName,
            fromDate = fromDate,
            toDate = toDate,
            orders = orders,
            summary = summary,
            sections = sections,
            failedSections = failed,
        )
    }

    // ─── Section mappers ────────────────────────────────────────────────────
    // Each helper calls the corresponding AnalyticsQueryService method and flattens
    // the result into 1+ ExportSection. Breaking up into multiple sections when the
    // DTO carries logically separate tables keeps each sheet focused.

    private fun revenueSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getRevenueProfit(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Revenue by Payment",
                title = "Revenue by Payment Method",
                columns = listOf("Method", "Revenue", "Order Count"),
                rows = data.payment_methods.map { listOf(it.method, fmt(it.revenue), it.order_count.toString()) },
            ),
            ExportSection(
                sheetName = "Revenue Daily",
                title = "Daily Revenue Trend",
                columns = listOf("Date", "Revenue"),
                rows = data.daily_trend.map { listOf(it.date, fmt(it.revenue)) },
            ),
        )
    }

    private fun ordersIntelligenceSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getOrdersIntelligence(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Orders by Channel",
                title = "Orders by Channel",
                columns = listOf("Channel", "Count", "Percent"),
                rows = data.channel_breakdown.map { listOf(it.channel, it.count.toString(), fmt(it.percent) + "%") },
            ),
            ExportSection(
                sheetName = "Orders Daily",
                title = "Daily Orders Trend",
                columns = listOf("Date", "Total", "Completed", "Cancelled"),
                rows = data.daily_trend.map { listOf(it.date, it.total.toString(), it.completed.toString(), it.cancelled.toString()) },
            ),
        )
    }

    private fun peakTimesSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getPeakTimes(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Hourly Distribution",
                title = "Orders by Hour of Day",
                columns = listOf("Hour", "Orders", "Revenue"),
                rows = data.hourly_data.map { listOf(it.hour.toString().padStart(2, '0') + ":00", it.order_count.toString(), fmt(it.revenue)) },
            ),
            ExportSection(
                sheetName = "Day of Week",
                title = "Orders by Day of Week",
                columns = listOf("Day", "Orders", "Revenue"),
                rows = data.day_of_week.map { listOf(it.name, it.order_count.toString(), fmt(it.revenue)) },
            ),
        )
    }

    private fun cashierSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getCashierPerformance(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Cashier Performance",
                title = "Cashier Performance",
                columns = listOf("Cashier", "Revenue", "Orders", "Avg Order Value", "Cancelled", "Cancellation Rate"),
                rows = data.map { listOf(it.cashier_name, fmt(it.revenue), it.order_count.toString(), fmt(it.average_order_value), it.cancelled_orders.toString(), fmt(it.cancellation_rate) + "%") },
            ),
        )
    }

    private fun deliverySections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getDeliveryPerformance(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Delivery Performance",
                title = "Delivery Performance",
                columns = listOf("Driver", "Orders Completed", "Fees Collected", "Revenue", "Avg Delivery Time (min)", "Late %"),
                rows = data.map { listOf(it.driver_name, it.orders_completed.toString(), fmt(it.fees_collected), fmt(it.revenue), fmt(it.avg_delivery_time_minutes), fmt(it.late_delivery_percent) + "%") },
            ),
        )
    }

    private fun productSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getProductIntelligence(v, from, to, limit = 100)
        // All four product lists share the ProductItemDto shape — same columns, same rendering.
        fun prod(
            title: String,
            sheet: String,
            items: List<net.marllex.waselak.backend.api.routes.ProductItemDto>,
        ): ExportSection = ExportSection(
            sheetName = sheet,
            title = title,
            columns = listOf("Item", "Category", "Qty Sold", "Revenue", "Cost", "Profit %"),
            rows = items.map { p ->
                listOf(
                    p.item_name, p.category_name, p.quantity_sold.toString(),
                    fmt(p.revenue), fmt(p.cost_price), fmt(p.profit_margin) + "%",
                )
            },
        )
        return listOf(
            prod("Top-Selling Products", "Top-Selling", data.top_selling),
            prod("Most-Profitable Products", "Most-Profitable", data.most_profitable),
            prod("Least-Selling Products", "Least-Selling", data.least_selling),
            prod("Low-Margin Warnings", "Low-Margin", data.low_margin_warnings),
            ExportSection(
                sheetName = "Revenue by Category",
                title = "Revenue by Category",
                columns = listOf("Category", "Revenue", "Items Count"),
                rows = data.revenue_by_category.map { listOf(it.category_name, fmt(it.revenue), it.item_count.toString()) },
            ),
        )
    }

    private fun customerSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getCustomerIntelligence(v, from, to)
        val overview = ExportSection(
            sheetName = "Customers Overview",
            title = "Customer Overview",
            columns = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Total Customers", data.total_customers.toString()),
                listOf("New Customers %", fmt(data.new_customers_percent) + "%"),
                listOf("Returning Customers %", fmt(data.returning_customers_percent) + "%"),
                listOf("Average Spend", fmt(data.average_spend)),
                listOf("Lifetime Value", fmt(data.lifetime_value)),
            ) + data.frequency_buckets.map { listOf("Frequency: " + it.key, it.value.toString()) },
        )
        val top = ExportSection(
            sheetName = "Top Customers",
            title = "Top Customers",
            columns = listOf("Customer", "Phone", "Orders", "Total Spent"),
            rows = data.top_customers.map { listOf(it.customer_name, it.phone, it.order_count.toString(), fmt(it.total_spent)) },
        )
        return listOf(overview, top)
    }

    private fun alertsSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getAlerts(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Alerts",
                title = "Active Alerts",
                columns = listOf("Type", "Severity", "Title", "Message", "Value", "Threshold"),
                rows = data.alerts.map { listOf(it.type, it.severity, it.title, it.message, fmt(it.value), fmt(it.threshold)) },
            ),
        )
    }

    private fun stockSections(v: UUID): List<ExportSection> {
        val data = analyticsQueryService.getStockOverview(v)
        val overview = ExportSection(
            sheetName = "Stock Overview",
            title = "Stock Overview",
            columns = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Total Items", data.total_items.toString()),
                listOf("Total Stock Value (Cost)", fmt(data.total_stock_value)),
                listOf("Total Selling Value", fmt(data.total_selling_value)),
                listOf("Potential Profit", fmt(data.potential_profit)),
                listOf("Low Stock Items", data.low_stock_items.size.toString()),
                listOf("Out of Stock", data.out_of_stock_items.size.toString()),
                listOf("Dead Stock Items", data.dead_stock_items.size.toString()),
            ),
        )
        fun items(title: String, sheet: String, list: List<net.marllex.waselak.backend.api.routes.StockOverviewItemDto>) =
            ExportSection(
                sheetName = sheet, title = title,
                columns = listOf("Item", "Quantity", "Min Quantity", "Unit", "Cost Price", "Status"),
                rows = list.map { listOf(it.item_name, fmt(it.quantity), fmt(it.min_quantity), it.unit, fmt(it.cost_price), it.status) },
            )
        return listOf(
            overview,
            items("Low Stock Items", "Stock Low", data.low_stock_items),
            items("Out of Stock Items", "Stock Out", data.out_of_stock_items),
            items("Dead Stock Items", "Stock Dead", data.dead_stock_items),
        )
    }

    private fun offersSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getOffersAnalytics(v, from, to)
        val overview = ExportSection(
            sheetName = "Offers Overview",
            title = "Offers Overview",
            columns = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Total Offers", data.total_offers.toString()),
                listOf("Active Offers", data.active_offers.toString()),
                listOf("Total Uses", data.total_offer_uses.toString()),
                listOf("Total Discount", fmt(data.total_discount_from_offers)),
                listOf("Avg Discount / Use", fmt(data.average_discount_per_use)),
            ),
        )
        val top = ExportSection(
            sheetName = "Top Offers",
            title = "Top Offers",
            columns = listOf("Offer", "Type", "Value", "Uses", "Discount Given", "Revenue", "Promo Code"),
            rows = data.top_offers.map {
                listOf(
                    it.offer_name, it.discount_type, fmt(it.discount_value), it.usage_count.toString(),
                    fmt(it.total_discount_given), fmt(it.total_revenue_from_offer_orders), it.promo_code ?: "",
                )
            },
        )
        return listOf(overview, top)
    }

    private fun discountsSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getDiscountAnalytics(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Discounts Overview",
                title = "Discounts Overview",
                columns = listOf("Metric", "Value"),
                rows = listOf(
                    listOf("Orders With Discount", data.total_orders_with_discount.toString()),
                    listOf("Total Discount Given", fmt(data.total_discount_given)),
                    listOf("Avg Discount / Order", fmt(data.average_discount_per_order)),
                    listOf("Discount Rate", fmt(data.discount_rate) + "%"),
                ),
            ),
            ExportSection(
                sheetName = "Discounts Breakdown",
                title = "Discounts by Type",
                columns = listOf("Type", "Count", "Total Amount", "Percent of Total"),
                rows = data.breakdown.map { listOf(it.type, it.count.toString(), fmt(it.total_amount), fmt(it.percent_of_total) + "%") },
            ),
        )
    }

    private fun loyaltySections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getLoyaltyAnalytics(v, from, to)
        return listOf(
            ExportSection(
                sheetName = "Loyalty",
                title = "Loyalty Overview",
                columns = listOf("Metric", "Value"),
                rows = listOf(
                    listOf("Total Points Earned", data.total_points_earned.toString()),
                    listOf("Total Points Redeemed", data.total_points_redeemed.toString()),
                    listOf("Total Points Outstanding", data.total_points_outstanding.toString()),
                    listOf("Active Loyalty Customers", data.active_loyalty_customers.toString()),
                    listOf("Redemption Rate", fmt(data.redemption_rate) + "%"),
                    listOf("Points → Revenue", fmt(data.points_to_revenue)),
                ),
            ),
        )
    }

    private fun staffCostSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getStaffCostsAnalytics(v, from, to)
        val overview = ExportSection(
            sheetName = "Staff Costs",
            title = "Staff Costs Summary",
            columns = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Workers", data.workers_count.toString()),
                listOf("Total Salaries", fmt(data.total_salaries)),
                listOf("Total Overtime", fmt(data.total_overtime)),
                listOf("Total Compensation", fmt(data.total_compensation)),
                listOf("Paid Amount", fmt(data.paid_amount)),
                listOf("Unpaid Amount", fmt(data.unpaid_amount)),
                listOf("Overtime Hours", fmt(data.overtime_hours)),
                listOf("Overtime %", fmt(data.overtime_percentage) + "%"),
            ),
        )
        val topOT = ExportSection(
            sheetName = "Top Overtime",
            title = "Top Overtime Workers",
            columns = listOf("Worker", "Overtime Hours", "Overtime Amount"),
            rows = data.top_overtime_workers.map { listOf(it.worker_name, fmt(it.overtime_hours), fmt(it.overtime_amount)) },
        )
        return listOf(overview, topOT)
    }

    private fun supplierSections(v: UUID, from: Instant, to: Instant): List<ExportSection> {
        val data = analyticsQueryService.getSupplierAnalytics(v, from, to)
        val overview = ExportSection(
            sheetName = "Suppliers Overview",
            title = "Suppliers Overview",
            columns = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Total Suppliers", data.total_suppliers.toString()),
                listOf("Active Suppliers", data.active_suppliers.toString()),
                listOf("Total POs", data.total_purchase_orders.toString()),
                listOf("Received", data.received_orders.toString()),
                listOf("Pending", data.pending_orders.toString()),
                listOf("Total Spent", fmt(data.total_spent)),
                listOf("Avg PO Value", fmt(data.average_order_value)),
            ),
        )
        val top = ExportSection(
            sheetName = "Top Suppliers",
            title = "Top Suppliers by Spend",
            columns = listOf("Supplier", "Total Orders", "Total Spent", "Received", "Pending"),
            rows = data.top_suppliers.map {
                listOf(it.supplier_name, it.total_orders.toString(), fmt(it.total_spent), it.received_orders.toString(), it.pending_orders.toString())
            },
        )
        return listOf(overview, top)
    }

    private fun fmt(d: Double): String = String.format(Locale.US, "%.2f", d)

    // ─── PDF generation ─────────────────────────────────────────────────────

    fun generatePDF(data: ExportData): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Cover
        document.add(
            Paragraph("${data.vendorName} — Full Analytics Report")
                .setFontSize(20f).setBold().setTextAlignment(TextAlignment.CENTER)
        )
        document.add(
            Paragraph("Period: ${dateFmt.format(Date(data.fromDate))} → ${dateFmt.format(Date(data.toDate))}")
                .setFontSize(12f).setTextAlignment(TextAlignment.CENTER)
        )
        document.add(Paragraph("\n"))

        // Summary
        document.add(Paragraph("Summary").setFontSize(16f).setBold())
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
        summaryTable.addCell(kv("Total Orders", data.summary.totalOrders.toString()))
        summaryTable.addCell(kv("Total Revenue (EGP)", fmt(data.summary.totalRevenue)))
        if (data.summary.totalDeliveryFees > 0) {
            summaryTable.addCell(kv("Total Delivery Fees (EGP)", fmt(data.summary.totalDeliveryFees)))
        }
        document.add(summaryTable)
        document.add(Paragraph("\n"))

        // One titled sub-table per analytics section. Cap row count so PDF stays usable
        // — merchants who need every single row should use the Excel export instead.
        val maxRowsPerSection = 50
        data.sections.forEach { section ->
            document.add(Paragraph(section.title).setFontSize(14f).setBold())
            if (section.rows.isEmpty()) {
                document.add(Paragraph("(no data)").setFontSize(10f).setItalic())
                document.add(Paragraph("\n"))
                return@forEach
            }
            val cols = section.columns.size
            val widths = FloatArray(cols) { 100f / cols }
            val table = Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth()
            section.columns.forEach { table.addHeaderCell(headerCell(it)) }
            section.rows.take(maxRowsPerSection).forEach { row ->
                row.forEach { table.addCell(bodyCell(it)) }
            }
            document.add(table)
            if (section.rows.size > maxRowsPerSection) {
                document.add(
                    Paragraph("… and ${section.rows.size - maxRowsPerSection} more rows (see Excel export for full list)")
                        .setFontSize(9f).setItalic()
                )
            }
            document.add(Paragraph("\n"))
        }

        // Orders detailed (kept last since it's the biggest)
        document.add(Paragraph("Orders").setFontSize(16f).setBold())
        val ordersTable = Table(UnitValue.createPercentArray(floatArrayOf(10f, 15f, 15f, 15f, 15f, 15f, 15f))).useAllAvailableWidth()
        listOf("Order #", "Date", "Channel", "Payment", "Status", "Customer", "Total")
            .forEach { ordersTable.addHeaderCell(headerCell(it)) }
        data.orders.take(200).forEach { order ->
            ordersTable.addCell(bodyCell(order.orderNumber))
            ordersTable.addCell(bodyCell("${order.date}\n${order.time}"))
            ordersTable.addCell(bodyCell(order.channel))
            ordersTable.addCell(bodyCell(order.paymentMethod))
            ordersTable.addCell(bodyCell(order.status))
            ordersTable.addCell(bodyCell(order.customerName ?: order.customerPhone ?: "N/A"))
            ordersTable.addCell(bodyCell(fmt(order.total)))
        }
        document.add(ordersTable)
        if (data.orders.size > 200) {
            document.add(
                Paragraph("… and ${data.orders.size - 200} more orders (see Excel export)")
                    .setFontSize(9f).setItalic()
            )
        }

        // Errors page — only shown if something failed, so merchant knows
        if (data.failedSections.isNotEmpty()) {
            document.add(Paragraph("\n"))
            document.add(Paragraph("Sections with errors").setFontSize(14f).setBold())
            val errTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
            errTable.addHeaderCell(headerCell("Section"))
            errTable.addHeaderCell(headerCell("Error"))
            data.failedSections.forEach {
                errTable.addCell(bodyCell(it.title))
                errTable.addCell(bodyCell(it.error))
            }
            document.add(errTable)
        }

        document.close()
        return outputStream.toByteArray()
    }

    // ─── Excel generation ───────────────────────────────────────────────────

    fun generateExcel(data: ExportData): ByteArray {
        val workbook: Workbook = XSSFWorkbook()
        val headerStyle = createHeaderStyle(workbook)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Summary sheet
        val summarySheet = workbook.createSheet("Summary")
        var rowNum = 0
        fun summaryRow(a: String, b: String) {
            val r = summarySheet.createRow(rowNum++)
            r.createCell(0).setCellValue(a)
            r.createCell(1).setCellValue(b)
        }
        summarySheet.createRow(rowNum++).also {
            val c = it.createCell(0); c.setCellValue("${data.vendorName} — Full Analytics Report"); c.cellStyle = headerStyle
        }
        summaryRow("Period", "${dateFmt.format(Date(data.fromDate))} → ${dateFmt.format(Date(data.toDate))}")
        rowNum++
        summaryRow("Total Orders", data.summary.totalOrders.toString())
        summaryRow("Total Revenue (EGP)", fmt(data.summary.totalRevenue))
        if (data.summary.totalDeliveryFees > 0) summaryRow("Total Delivery Fees (EGP)", fmt(data.summary.totalDeliveryFees))
        rowNum++
        summarySheet.createRow(rowNum++).also {
            val c = it.createCell(0); c.setCellValue("Orders by Channel"); c.cellStyle = headerStyle
        }
        data.summary.ordersByChannel.forEach { (k, v) -> summaryRow(k, v.toString()) }
        rowNum++
        summarySheet.createRow(rowNum++).also {
            val c = it.createCell(0); c.setCellValue("Orders by Payment Method"); c.cellStyle = headerStyle
        }
        data.summary.ordersByPaymentMethod.forEach { (k, v) -> summaryRow(k, v.toString()) }
        rowNum++
        summarySheet.createRow(rowNum++).also {
            val c = it.createCell(0); c.setCellValue("Orders by Status"); c.cellStyle = headerStyle
        }
        data.summary.ordersByStatus.forEach { (k, v) -> summaryRow(k, v.toString()) }
        summarySheet.autoSizeColumn(0); summarySheet.autoSizeColumn(1)

        // One sheet per analytics section
        data.sections.forEach { section ->
            val sheet = workbook.createSheet(safeSheetName(section.sheetName, workbook))
            var sr = 0
            val header = sheet.createRow(sr++)
            section.columns.forEachIndexed { i, col ->
                val c = header.createCell(i); c.setCellValue(col); c.cellStyle = headerStyle
            }
            section.rows.forEach { row ->
                val r = sheet.createRow(sr++)
                row.forEachIndexed { i, cellValue ->
                    val c = r.createCell(i)
                    val asNumber = cellValue.replace(",", "").toDoubleOrNull()
                    if (asNumber != null && !cellValue.endsWith("%") && !cellValue.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        c.setCellValue(asNumber)
                    } else {
                        c.setCellValue(cellValue)
                    }
                }
            }
            for (i in section.columns.indices) sheet.autoSizeColumn(i)
        }

        // Orders sheet — full detail so the merchant can audit every transaction
        val ordersSheet = workbook.createSheet("Orders")
        val orderHeaders = listOf(
            "Order #", "Date", "Time", "Channel", "Payment Method", "Status",
            "Customer Name", "Customer Phone", "Subtotal", "Delivery Fees", "Total",
            "Cashier", "Delivery Person",
        )
        ordersSheet.createRow(0).also { r ->
            orderHeaders.forEachIndexed { i, h ->
                val c = r.createCell(i); c.setCellValue(h); c.cellStyle = headerStyle
            }
        }
        data.orders.forEachIndexed { idx, order ->
            val r = ordersSheet.createRow(idx + 1)
            r.createCell(0).setCellValue(order.orderNumber)
            r.createCell(1).setCellValue(order.date)
            r.createCell(2).setCellValue(order.time)
            r.createCell(3).setCellValue(order.channel)
            r.createCell(4).setCellValue(order.paymentMethod)
            r.createCell(5).setCellValue(order.status)
            r.createCell(6).setCellValue(order.customerName ?: "")
            r.createCell(7).setCellValue(order.customerPhone ?: "")
            r.createCell(8).setCellValue(order.subtotal)
            r.createCell(9).setCellValue(order.deliveryFee)
            r.createCell(10).setCellValue(order.total)
            r.createCell(11).setCellValue(order.cashierName ?: "")
            r.createCell(12).setCellValue(order.deliveryPersonName ?: "")
        }
        for (i in orderHeaders.indices) ordersSheet.autoSizeColumn(i)

        // Order items — one row per item
        val itemsSheet = workbook.createSheet("Order Items")
        val itemHeaders = listOf("Order #", "Item Name", "Quantity", "Price", "Total")
        itemsSheet.createRow(0).also { r ->
            itemHeaders.forEachIndexed { i, h ->
                val c = r.createCell(i); c.setCellValue(h); c.cellStyle = headerStyle
            }
        }
        var itRow = 1
        data.orders.forEach { order ->
            order.items.forEach { item ->
                val r = itemsSheet.createRow(itRow++)
                r.createCell(0).setCellValue(order.orderNumber)
                r.createCell(1).setCellValue(item.itemName)
                r.createCell(2).setCellValue(item.quantity.toDouble())
                r.createCell(3).setCellValue(item.price)
                r.createCell(4).setCellValue(item.total)
            }
        }
        for (i in itemHeaders.indices) itemsSheet.autoSizeColumn(i)

        // Errors sheet — only if anything failed. Keeps silent partial exports from
        // hiding the fact that data is missing.
        if (data.failedSections.isNotEmpty()) {
            val errSheet = workbook.createSheet("_Errors")
            errSheet.createRow(0).also { r ->
                val c0 = r.createCell(0); c0.setCellValue("Section"); c0.cellStyle = headerStyle
                val c1 = r.createCell(1); c1.setCellValue("Error"); c1.cellStyle = headerStyle
            }
            data.failedSections.forEachIndexed { i, f ->
                val r = errSheet.createRow(i + 1)
                r.createCell(0).setCellValue(f.title)
                r.createCell(1).setCellValue(f.error)
            }
            errSheet.autoSizeColumn(0); errSheet.autoSizeColumn(1)
        }

        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()
        return out.toByteArray()
    }

    // ─── Small helpers ─────────────────────────────────────────────────────

    /**
     * Excel sheet names are capped at 31 characters, must be unique within a workbook,
     * and can't contain `\ / ? * [ ] :`. Truncate + disambiguate to keep POI happy.
     */
    private fun safeSheetName(desired: String, workbook: Workbook): String {
        val cleaned = desired.replace(Regex("[\\\\/?*\\[\\]:]"), "-").take(31)
        var candidate = cleaned.ifBlank { "Sheet" }
        var i = 1
        while (workbook.getSheet(candidate) != null) {
            val suffix = " ($i)"
            candidate = cleaned.take(31 - suffix.length) + suffix
            i++
        }
        return candidate
    }

    private fun kv(k: String, v: String): Cell {
        // Key/value row for the summary table: key on the left bolded, value on the right.
        val keyCell = Cell().add(Paragraph(k).setBold())
        return keyCell.add(Paragraph(v))
    }

    private fun headerCell(content: String): Cell =
        Cell().add(Paragraph(content).setBold())
            .setBackgroundColor(DeviceRgb(200, 200, 200))
            .setTextAlignment(TextAlignment.CENTER)

    private fun bodyCell(content: String): Cell = Cell().add(Paragraph(content))

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
