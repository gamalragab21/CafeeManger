package net.marllex.waselak.backend.domain.service

import kotlinx.datetime.*
import net.marllex.waselak.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Service for processing chatbot queries and generating responses
 */
class ChatbotService {
    
    /**
     * Process a natural language query and return a response
     */
    fun processQuery(
        query: String,
        vendorId: UUID,
        language: String = "en"
    ): ChatbotResponse {
        val normalizedQuery = query.lowercase().trim()
        
        // Simple rule-based intent classification
        val intent = classifyIntent(normalizedQuery)
        val entities = extractEntities(normalizedQuery)
        
        return when (intent) {
            Intent.SALES_TODAY -> handleSalesToday(vendorId, language)
            Intent.SALES_WEEK -> handleSalesWeek(vendorId, language)
            Intent.SALES_MONTH -> handleSalesMonth(vendorId, language)
            Intent.ORDERS_TODAY -> handleOrdersToday(vendorId, language)
            Intent.STAFF_ABSENT -> handleStaffAbsent(vendorId, language)
            Intent.TOP_ITEMS -> handleTopItems(vendorId, language, entities)
            Intent.DELIVERY_PERFORMANCE -> handleDeliveryPerformance(vendorId, language)
            Intent.GREETING -> handleGreeting(language)
            else -> handleUnknown(language)
        }
    }
    
    /**
     * Get quick suggestions for the user
     */
    fun getQuickSuggestions(language: String = "en"): List<QuickSuggestion> {
        return if (language == "ar") {
            listOf(
                QuickSuggestion("ما هي إجمالي المبيعات اليوم؟", "SALES", "💰"),
                QuickSuggestion("من هو غائب اليوم؟", "STAFF", "👥"),
                QuickSuggestion("أفضل العناصر مبيعاً هذا الأسبوع", "ANALYTICS", "⭐"),
                QuickSuggestion("كم عدد الطلبات اليوم؟", "ORDERS", "📦")
            )
        } else {
            listOf(
                QuickSuggestion("What are today's total sales?", "SALES", "💰"),
                QuickSuggestion("Who is absent today?", "STAFF", "👥"),
                QuickSuggestion("Top selling items this week", "ANALYTICS", "⭐"),
                QuickSuggestion("How many orders today?", "ORDERS", "📦")
            )
        }
    }
    
    // ─── Intent Classification ───────────────────────────────────
    
    private fun classifyIntent(query: String): Intent {
        return when {
            query.contains("sales") && query.contains("today") -> Intent.SALES_TODAY
            query.contains("مبيعات") && query.contains("اليوم") -> Intent.SALES_TODAY
            query.contains("sales") && (query.contains("week") || query.contains("this week")) -> Intent.SALES_WEEK
            query.contains("مبيعات") && query.contains("أسبوع") -> Intent.SALES_WEEK
            query.contains("sales") && query.contains("month") -> Intent.SALES_MONTH
            query.contains("مبيعات") && query.contains("شهر") -> Intent.SALES_MONTH
            query.contains("orders") && query.contains("today") -> Intent.ORDERS_TODAY
            query.contains("طلبات") && query.contains("اليوم") -> Intent.ORDERS_TODAY
            query.contains("absent") || query.contains("غائب") -> Intent.STAFF_ABSENT
            query.contains("top") && (query.contains("item") || query.contains("selling")) -> Intent.TOP_ITEMS
            query.contains("أفضل") && query.contains("مبيع") -> Intent.TOP_ITEMS
            query.contains("delivery") && query.contains("performance") -> Intent.DELIVERY_PERFORMANCE
            query.contains("hello") || query.contains("hi") || query.contains("مرحبا") -> Intent.GREETING
            else -> Intent.UNKNOWN
        }
    }
    
    private fun extractEntities(query: String): Map<String, String> {
        val entities = mutableMapOf<String, String>()
        
        // Extract time period
        when {
            query.contains("today") || query.contains("اليوم") -> entities["period"] = "today"
            query.contains("week") || query.contains("أسبوع") -> entities["period"] = "week"
            query.contains("month") || query.contains("شهر") -> entities["period"] = "month"
        }
        
        return entities
    }
    
    // ─── Query Handlers ──────────────────────────────────────────
    
    private fun handleSalesToday(vendorId: UUID, language: String): ChatbotResponse {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayStart = today.atTime(0, 0).toInstant(TimeZone.currentSystemDefault())
        val todayEnd = today.atTime(23, 59, 59).toInstant(TimeZone.currentSystemDefault())
        
        val result = transaction {
            OrdersTable
                .selectAll()
                .where { 
                    (OrdersTable.vendorId eq vendorId) and
                    (OrdersTable.createdAt greaterEq todayStart) and
                    (OrdersTable.createdAt lessEq todayEnd) and
                    (OrdersTable.status neq "CANCELLED")
                }
                .map { 
                    it[OrdersTable.total] to it[OrdersTable.id]
                }
        }
        
        val totalSales = result.sumOf { it.first.toDouble() }
        val orderCount = result.size
        
        val answer = if (language == "ar") {
            "إجمالي مبيعات اليوم هو ${String.format("%.2f", totalSales)} جنيه من $orderCount طلب."
        } else {
            "Today's total sales are ${String.format("%.2f", totalSales)} EGP from $orderCount orders."
        }
        
        return ChatbotResponse(
            answer = answer,
            data = mapOf(
                "type" to "SALES_SUMMARY",
                "values" to mapOf(
                    "totalSales" to totalSales.toString(),
                    "orderCount" to orderCount.toString(),
                    "averageOrderValue" to if (orderCount > 0) (totalSales / orderCount).toString() else "0"
                )
            ),
            visualFormat = "text",
            suggestions = if (language == "ar") {
                listOf("قارن مع الأمس", "أظهر أفضل العناصر")
            } else {
                listOf("Compare with yesterday", "Show top items")
            }
        )
    }
    
    private fun handleSalesWeek(vendorId: UUID, language: String): ChatbotResponse {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekAgo = today.minus(7, DateTimeUnit.DAY)
        val weekAgoStart = weekAgo.atTime(0, 0).toInstant(TimeZone.currentSystemDefault())
        
        val result = transaction {
            OrdersTable
                .selectAll()
                .where { 
                    (OrdersTable.vendorId eq vendorId) and
                    (OrdersTable.createdAt greaterEq weekAgoStart) and
                    (OrdersTable.status neq "CANCELLED")
                }
                .map { it[OrdersTable.total] }
        }
        
        val totalSales = result.sumOf { it.toDouble() }
        val orderCount = result.size
        
        val answer = if (language == "ar") {
            "إجمالي مبيعات هذا الأسبوع هو ${String.format("%.2f", totalSales)} جنيه من $orderCount طلب."
        } else {
            "This week's total sales are ${String.format("%.2f", totalSales)} EGP from $orderCount orders."
        }
        
        return ChatbotResponse(
            answer = answer,
            data = mapOf(
                "type" to "SALES_SUMMARY",
                "values" to mapOf(
                    "totalSales" to totalSales.toString(),
                    "orderCount" to orderCount.toString()
                )
            ),
            visualFormat = "text",
            suggestions = if (language == "ar") {
                listOf("قارن مع الأسبوع الماضي", "أظهر أفضل العناصر")
            } else {
                listOf("Compare with last week", "Show top items")
            }
        )
    }
    
    private fun handleSalesMonth(vendorId: UUID, language: String): ChatbotResponse {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val monthAgo = today.minus(30, DateTimeUnit.DAY)
        val monthAgoStart = monthAgo.atTime(0, 0).toInstant(TimeZone.currentSystemDefault())
        
        val result = transaction {
            OrdersTable
                .selectAll()
                .where { 
                    (OrdersTable.vendorId eq vendorId) and
                    (OrdersTable.createdAt greaterEq monthAgoStart) and
                    (OrdersTable.status neq "CANCELLED")
                }
                .map { it[OrdersTable.total] }
        }
        
        val totalSales = result.sumOf { it.toDouble() }
        val orderCount = result.size
        
        val answer = if (language == "ar") {
            "إجمالي مبيعات هذا الشهر هو ${String.format("%.2f", totalSales)} جنيه من $orderCount طلب."
        } else {
            "This month's total sales are ${String.format("%.2f", totalSales)} EGP from $orderCount orders."
        }
        
        return ChatbotResponse(
            answer = answer,
            data = mapOf(
                "type" to "SALES_SUMMARY",
                "values" to mapOf(
                    "totalSales" to totalSales.toString(),
                    "orderCount" to orderCount.toString()
                )
            ),
            visualFormat = "text",
            suggestions = if (language == "ar") {
                listOf("قارن مع الشهر الماضي", "أظهر التحليلات")
            } else {
                listOf("Compare with last month", "Show analytics")
            }
        )
    }
    
    private fun handleOrdersToday(vendorId: UUID, language: String): ChatbotResponse {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayStart = today.atTime(0, 0).toInstant(TimeZone.currentSystemDefault())
        val todayEnd = today.atTime(23, 59, 59).toInstant(TimeZone.currentSystemDefault())
        
        val result = transaction {
            OrdersTable
                .selectAll()
                .where { 
                    (OrdersTable.vendorId eq vendorId) and
                    (OrdersTable.createdAt greaterEq todayStart) and
                    (OrdersTable.createdAt lessEq todayEnd)
                }
                .groupBy { it[OrdersTable.status] }
                .mapValues { it.value.size }
        }
        
        val totalOrders = result.values.sum()
        
        val answer = if (language == "ar") {
            "لديك $totalOrders طلب اليوم."
        } else {
            "You have $totalOrders orders today."
        }
        
        return ChatbotResponse(
            answer = answer,
            data = mapOf(
                "type" to "LIST",
                "values" to result.mapKeys { entry ->
                    if (language == "ar") {
                        when (entry.key) {
                            "PENDING" -> "قيد الانتظار"
                            "PREPARING" -> "قيد التحضير"
                            "READY" -> "جاهز"
                            "COMPLETED" -> "مكتمل"
                            "CANCELLED" -> "ملغي"
                            else -> entry.key
                        }
                    } else {
                        entry.key
                    }
                }.mapValues { it.value.toString() }
            ),
            visualFormat = "list",
            suggestions = if (language == "ar") {
                listOf("أظهر المبيعات اليوم", "من هو غائب؟")
            } else {
                listOf("Show today's sales", "Who is absent?")
            }
        )
    }
    
    private fun handleStaffAbsent(vendorId: UUID, language: String): ChatbotResponse {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayStr = today.toString() // Format: YYYY-MM-DD
        
        val absentStaff = transaction {
            // Get all workers for this vendor
            val allWorkers = WorkersTable
                .selectAll()
                .where { (WorkersTable.vendorId eq vendorId) and (WorkersTable.active eq true) }
                .map { it[WorkersTable.id] to it[WorkersTable.fullName] }
            
            // Get workers who checked in today
            val presentWorkers = AttendanceTable
                .selectAll()
                .where { 
                    (AttendanceTable.vendorId eq vendorId) and
                    (AttendanceTable.date eq todayStr)
                }
                .map { it[AttendanceTable.workerId] }
                .toSet()
            
            allWorkers.filter { it.first !in presentWorkers }
        }
        
        val answer = if (absentStaff.isEmpty()) {
            if (language == "ar") {
                "جميع الموظفين حاضرون اليوم! 🎉"
            } else {
                "All staff members are present today! 🎉"
            }
        } else {
            if (language == "ar") {
                "الموظفون الغائبون اليوم: ${absentStaff.joinToString(", ") { it.second }}"
            } else {
                "Absent staff today: ${absentStaff.joinToString(", ") { it.second }}"
            }
        }
        
        return ChatbotResponse(
            answer = answer,
            data = if (absentStaff.isNotEmpty()) {
                mapOf(
                    "type" to "STAFF_LIST",
                    "values" to absentStaff.associate { it.first.toString() to it.second }
                )
            } else null,
            visualFormat = if (absentStaff.isNotEmpty()) "list" else "text",
            suggestions = if (language == "ar") {
                listOf("أظهر الحضور هذا الأسبوع", "أداء الموظفين")
            } else {
                listOf("Show attendance this week", "Staff performance")
            }
        )
    }
    
    private fun handleTopItems(vendorId: UUID, language: String, entities: Map<String, String>): ChatbotResponse {
        val period = entities["period"] ?: "week"
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startDate = when (period) {
            "today" -> today
            "month" -> today.minus(30, DateTimeUnit.DAY)
            else -> today.minus(7, DateTimeUnit.DAY)
        }
        val startInstant = startDate.atTime(0, 0).toInstant(TimeZone.currentSystemDefault())
        
        val topItems = transaction {
            val items = (OrderItemsTable innerJoin OrdersTable)
                .selectAll()
                .where {
                    (OrdersTable.vendorId eq vendorId) and
                    (OrdersTable.createdAt greaterEq startInstant) and
                    (OrdersTable.status neq "CANCELLED")
                }
                .map { row ->
                    Triple(
                        row[OrderItemsTable.itemNameSnapshot],
                        row[OrderItemsTable.quantity],
                        row[OrderItemsTable.quantity] * row[OrderItemsTable.itemPriceSnapshot].toDouble()
                    )
                }
            
            // Group by item name and aggregate
            items.groupBy { it.first }
                .map { (itemName, itemList) ->
                    val totalQuantity = itemList.sumOf { it.second }
                    val totalRevenue = itemList.sumOf { it.third }
                    Triple(itemName, totalQuantity, totalRevenue)
                }
                .sortedByDescending { it.third }
                .take(5)
        }
        
        val periodText = if (language == "ar") {
            when (period) {
                "today" -> "اليوم"
                "month" -> "هذا الشهر"
                else -> "هذا الأسبوع"
            }
        } else {
            when (period) {
                "today" -> "today"
                "month" -> "this month"
                else -> "this week"
            }
        }
        
        val answer = if (language == "ar") {
            "أفضل العناصر مبيعاً $periodText:\n" + 
            topItems.mapIndexed { index, (name, qty, revenue) ->
                "${index + 1}. $name - $qty وحدة (${String.format("%.2f", revenue)} جنيه)"
            }.joinToString("\n")
        } else {
            "Top selling items $periodText:\n" + 
            topItems.mapIndexed { index, (name, qty, revenue) ->
                "${index + 1}. $name - $qty units (${String.format("%.2f", revenue)} EGP)"
            }.joinToString("\n")
        }
        
        return ChatbotResponse(
            answer = answer,
            data = mapOf(
                "type" to "LIST",
                "values" to topItems.associate { triple ->
                    triple.first to "${triple.second} units - ${String.format("%.2f", triple.third)} EGP"
                }
            ),
            visualFormat = "list",
            suggestions = if (language == "ar") {
                listOf("أظهر المبيعات", "تحليلات المخزون")
            } else {
                listOf("Show sales", "Stock analytics")
            }
        )
    }
    
    private fun handleDeliveryPerformance(vendorId: UUID, language: String): ChatbotResponse {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekAgo = today.minus(7, DateTimeUnit.DAY)
        val weekAgoStart = weekAgo.atTime(0, 0).toInstant(TimeZone.currentSystemDefault())
        
        val performance = transaction {
            val orders = (OrdersTable innerJoin UsersTable)
                .selectAll()
                .where {
                    (OrdersTable.vendorId eq vendorId) and
                    (OrdersTable.createdAt greaterEq weekAgoStart) and
                    (OrdersTable.channel eq "DELIVERY") and
                    (OrdersTable.deliveryUserId.isNotNull())
                }
                .map { row ->
                    Triple(
                        row[UsersTable.name],
                        1, // count
                        row[OrdersTable.total].toDouble()
                    )
                }
            
            // Group by delivery user name
            orders.groupBy { it.first }
                .map { (name, orderList) ->
                    val orderCount = orderList.size
                    val totalRevenue = orderList.sumOf { it.third }
                    Triple(name, orderCount, totalRevenue)
                }
                .sortedByDescending { it.second }
        }
        
        val answer = if (language == "ar") {
            "أداء التوصيل هذا الأسبوع:\n" + 
            performance.mapIndexed { index, (name, count, revenue) ->
                "${index + 1}. $name - $count طلب (${String.format("%.2f", revenue)} جنيه)"
            }.joinToString("\n")
        } else {
            "Delivery performance this week:\n" + 
            performance.mapIndexed { index, (name, count, revenue) ->
                "${index + 1}. $name - $count orders (${String.format("%.2f", revenue)} EGP)"
            }.joinToString("\n")
        }
        
        return ChatbotResponse(
            answer = answer,
            data = mapOf(
                "type" to "LIST",
                "values" to performance.associate { triple ->
                    triple.first to "${triple.second} orders - ${String.format("%.2f", triple.third)} EGP"
                }
            ),
            visualFormat = "list",
            suggestions = if (language == "ar") {
                listOf("أظهر جميع الطلبات", "أداء الموظفين")
            } else {
                listOf("Show all orders", "Staff performance")
            }
        )
    }
    
    private fun handleGreeting(language: String): ChatbotResponse {
        val answer = if (language == "ar") {
            "مرحباً! أنا مساعدك الذكي. يمكنني مساعدتك في الاستعلام عن المبيعات والطلبات والموظفين والتحليلات. كيف يمكنني مساعدتك اليوم؟"
        } else {
            "Hello! I'm your AI assistant. I can help you with sales, orders, staff, and analytics queries. How can I help you today?"
        }
        
        return ChatbotResponse(
            answer = answer,
            visualFormat = "text",
            suggestions = if (language == "ar") {
                listOf("ما هي مبيعات اليوم؟", "من هو غائب؟", "أفضل العناصر")
            } else {
                listOf("What are today's sales?", "Who is absent?", "Top items")
            }
        )
    }
    
    private fun handleUnknown(language: String): ChatbotResponse {
        val answer = if (language == "ar") {
            "عذراً، لم أفهم سؤالك. يمكنك السؤال عن:\n" +
            "• المبيعات (اليوم، هذا الأسبوع، هذا الشهر)\n" +
            "• الطلبات والحالات\n" +
            "• حضور الموظفين\n" +
            "• أفضل العناصر مبيعاً\n" +
            "• أداء التوصيل"
        } else {
            "Sorry, I didn't understand your question. You can ask about:\n" +
            "• Sales (today, this week, this month)\n" +
            "• Orders and statuses\n" +
            "• Staff attendance\n" +
            "• Top selling items\n" +
            "• Delivery performance"
        }
        
        return ChatbotResponse(
            answer = answer,
            visualFormat = "text",
            suggestions = if (language == "ar") {
                listOf("مبيعات اليوم", "الطلبات اليوم", "من هو غائب؟")
            } else {
                listOf("Today's sales", "Today's orders", "Who is absent?")
            }
        )
    }
    
    // ─── Data Classes ────────────────────────────────────────────
    
    enum class Intent {
        SALES_TODAY,
        SALES_WEEK,
        SALES_MONTH,
        ORDERS_TODAY,
        STAFF_ABSENT,
        TOP_ITEMS,
        DELIVERY_PERFORMANCE,
        GREETING,
        UNKNOWN
    }
    
    data class ChatbotResponse(
        val answer: String,
        val data: Map<String, Any>? = null,
        val visualFormat: String = "text",
        val suggestions: List<String> = emptyList()
    )
    
    data class QuickSuggestion(
        val text: String,
        val category: String,
        val icon: String
    )
}
