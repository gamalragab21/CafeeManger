package net.marllex.cafeemanger.core.network.mapper

import net.marllex.cafeemanger.core.model.*
import net.marllex.cafeemanger.core.network.dto.*

// ─── Vendor Mappers ──────────────────────────────────────────────
fun VendorResponse.toDomain() = Vendor(
    id = id,
    name = name,
    logoUrl = logoUrl,
    address = address,
    contactPhone = contactPhone,
    walletPhone = walletPhone,
    defaultDeliveryFee = defaultDeliveryFee,
    storeType = storeType,
    enableTables = enableTables,
    enableDineIn = enableDineIn,
    enableDelivery = enableDelivery,
    digitalMenuUrl = digitalMenuUrl,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ─── User Mappers ────────────────────────────────────────────────
fun UserResponse.toDomain() = User(
    id = id,
    vendorId = vendorId,
    role = UserRole.valueOf(role),
    name = name,
    phone = phone,
    email = email,
    active = active,
    createdAt = createdAt
)

// ─── Category Mappers ────────────────────────────────────────────
fun CategoryResponse.toDomain() = Category(
    id = id,
    vendorId = vendorId,
    name = name,
    displayOrder = displayOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ─── Item Mappers ────────────────────────────────────────────────
fun ItemResponse.toDomain() = Item(
    id = id,
    vendorId = vendorId,
    categoryId = categoryId,
    name = name,
    description = description,
    price = price,
    imageUrl = imageUrl,
    available = available,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ─── Table Mappers ───────────────────────────────────────────────
fun TableResponse.toDomain() = Table(
    id = id,
    vendorId = vendorId,
    number = number,
    capacity = capacity,
    status = TableStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ─── Order Mappers ───────────────────────────────────────────────
fun OrderResponse.toDomain() = Order(
    id = id,
    vendorId = vendorId,
    channel = OrderChannel.valueOf(channel),
    status = OrderStatus.valueOf(status),
    tableId = tableId,
    cashierId = cashierId,
    cashierName = cashierName,
    deliveryUserId = deliveryUserId,
    deliveryUserName = deliveryUserName,
    clientName = clientName,
    clientPhone = clientPhone,
    clientAddress = clientAddress,
    geoLat = geoLat,
    geoLng = geoLng,
    paymentMethod = PaymentMethod.valueOf(paymentMethod),
    subtotal = subtotal,
    deliveryFee = deliveryFee,
    tax = tax,
    total = total,
    notes = notes,
    items = items.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderItemResponse.toDomain() = OrderItem(
    id = id,
    orderId = orderId,
    itemId = itemId,
    itemNameSnapshot = itemNameSnapshot,
    itemPriceSnapshot = itemPriceSnapshot,
    quantity = quantity,
    note = note
)

// ─── Stock Mappers ──────────────────────────────────────────────
fun StockResponse.toDomain() = Stock(
    id = id,
    vendorId = vendorId,
    itemId = itemId,
    itemName = itemName,
    quantity = quantity,
    minQuantity = minQuantity,
    costPrice = costPrice,
    unit = unit,
    lastUpdatedAt = updatedAt ?: (createdAt ?: System.currentTimeMillis()),
)

fun StockTransactionResponse.toDomain() = StockTransaction(
    id = id,
    stockId = stockId,
    type = StockTransactionType.valueOf(type),
    quantity = quantity,
    previousQuantity = previousQuantity,
    orderId = orderId,
    note = note,
    createdAt = createdAt ?: System.currentTimeMillis(),
)

// ─── Analytics Mappers ───────────────────────────────────────────
fun AnalyticsSummaryResponse.toDomain(from: Long, to: Long) = AnalyticsSummary(
    totalOrders = totalOrders,
    totalRevenue = totalRevenue,
    averageOrderValue = averageOrderValue,
    ordersByChannel = ordersByChannel,
    ordersByStatus = ordersByStatus,
    ordersByPaymentMethod = ordersByPaymentMethod,
    revenueByPaymentMethod = revenueByPaymentMethod,
    topItems = topItems.map { it.toDomain() },
    fromDate = from,
    toDate = to
)

fun TopItemResponse.toDomain() = TopItem(
    item = item,
    quantitySold = quantitySold,
    revenue = revenue
)

fun DailyAnalyticsResponse.toDomain() = DailyAnalytics(
    date = date,
    orders = orders,
    revenue = revenue
)

fun SettlementByPaymentMethodResponse.toDomain() = SettlementByPaymentMethod(
    orderCount = orderCount,
    totalRevenue = totalRevenue,
    totalTax = totalTax,
    totalSubtotal = totalSubtotal
)

fun SettlementsResponse.toDomain() = Settlements(
    byPaymentMethod = byPaymentMethod.mapValues { it.value.toDomain() }
)

fun DeliveryPerformanceResponse.toDomain() = DeliveryPerformance(
    deliveryUserId = deliveryUserId,
    deliveryUserName = deliveryUserName,
    orderCount = orderCount,
    totalRevenue = totalRevenue,
    totalTax = totalTax
)
