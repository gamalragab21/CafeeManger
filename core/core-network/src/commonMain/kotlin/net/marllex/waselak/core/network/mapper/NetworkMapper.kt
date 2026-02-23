package net.marllex.waselak.core.network.mapper

import kotlinx.datetime.Clock
import net.marllex.waselak.core.model.*
import net.marllex.waselak.core.network.dto.*

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
    enableTakeaway = enableTakeaway,
    enableInStore = enableInStore,
    enablePickupLater = enablePickupLater,
    businessType = businessType,
    taxEnabled = taxEnabled,
    defaultTaxPercent = defaultTaxPercent,
    stockMode = stockMode,
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
    costPrice = costPrice,
    sku = sku,
    barcode = barcode,
    imageUrl = imageUrl,
    available = available,
    stockBehavior = stockBehavior,
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
    status = OrderStatus.parse(status),
    tableId = tableId,
    tableNumber = tableNumber,
    cashierId = cashierId,
    cashierName = cashierName,
    deliveryUserId = deliveryUserId,
    deliveryUserName = deliveryUserName,
    clientName = clientName,
    clientPhone = clientPhone,
    clientAddress = clientAddress,
    customerId = customerId,
    geoLat = geoLat,
    geoLng = geoLng,
    paymentMethod = PaymentMethod.valueOf(paymentMethod),
    paymentStatus = PaymentStatus.valueOf(paymentStatus),
    paymentTiming = PaymentTiming.valueOf(paymentTiming),
    paymentConfirmedAt = paymentConfirmedAt,
    paymentConfirmedBy = paymentConfirmedBy,
    subtotal = subtotal,
    deliveryFee = deliveryFee,
    discount = discount,
    discountType = discountType,
    tax = tax,
    taxPercent = taxPercent,
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
    baseUnit = baseUnit,
    conversionRate = conversionRate,
    isMenuItem = isMenuItem,
    alertEnabled = alertEnabled,
    lastUpdatedAt = updatedAt ?: (createdAt ?: Clock.System.now().toEpochMilliseconds()),
)

fun StockTransactionResponse.toDomain() = StockTransaction(
    id = id,
    stockId = stockId,
    itemName = itemName,
    type = try { StockTransactionType.valueOf(type) } catch (_: Exception) { StockTransactionType.ADJUST },
    quantity = quantity,
    previousQuantity = previousQuantity,
    orderId = orderId,
    recipeId = recipeId,
    note = note,
    createdAt = createdAt ?: Clock.System.now().toEpochMilliseconds(),
)

fun StockAlertResponse.toDomain() = StockAlert(
    id = id,
    itemName = itemName,
    quantity = quantity,
    minQuantity = minQuantity,
    unit = unit,
    isOutOfStock = isOutOfStock,
    isMenuItem = isMenuItem,
)

fun StockAnalyticsSummaryResponse.toDomain() = StockSummary(
    totalItems = totalItems,
    totalValue = totalValue,
    lowStockCount = lowStockCount,
    outOfStockCount = outOfStockCount,
    healthyStockCount = healthyCount,
    menuItemsCount = menuItemsCount,
    independentItemsCount = independentItemsCount,
    recipeItemsCount = recipeItemsCount,
    totalTransactionsToday = totalTransactionsToday,
    totalAddedToday = totalAddedToday,
    totalDeductedToday = totalDeductedToday,
)

// ─── Recipe Mappers ──────────────────────────────────────────────
fun RecipeResponse.toDomain() = Recipe(
    id = id,
    vendorId = vendorId,
    itemId = itemId,
    itemName = itemName,
    name = name,
    description = description,
    yieldQuantity = yieldQuantity,
    yieldUnit = yieldUnit,
    active = active,
    ingredients = ingredients.map { it.toDomain() },
    totalCost = totalCost,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecipeIngredientResponse.toDomain() = RecipeIngredient(
    stockId = stockId,
    stockItemName = stockItemName,
    quantity = quantity,
    unit = unit,
    displayOrder = displayOrder,
    availableQuantity = availableQuantity,
)

fun RecipeAvailabilityResponse.toDomain() = RecipeAvailability(
    recipeId = recipeId,
    itemName = itemName,
    available = available,
    maxServings = maxServings,
    requestedServings = requestedServings,
    insufficientIngredients = insufficientIngredients.map { it.toDomain() },
)

fun InsufficientIngredientResponse.toDomain() = InsufficientIngredient(
    stockId = stockId,
    stockItemName = stockItemName,
    required = required,
    available = available,
    unit = unit,
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
    totalDeliveryFees = totalDeliveryFees,
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
    totalDeliveryFees = totalDeliveryFees
)

// ─── Analytics Dashboard V2 Mappers ─────────────────────────────────

fun PeriodMetricsResponse.toDomain() = PeriodMetrics(
    totalRevenue = totalRevenue,
    totalOrders = totalOrders,
    averageOrderValue = averageOrderValue,
    totalDeliveryFees = totalDeliveryFees,
    totalDiscounts = totalDiscounts,
)

fun ExecutiveSummaryResponse.toDomain() = ExecutiveSummary(
    current = current.toDomain(),
    previous = previous.toDomain(),
    revenueChangePercent = revenueChangePercent,
    ordersChangePercent = ordersChangePercent,
    aovChangePercent = aovChangePercent,
    activeOrders = activeOrders,
    attendanceToday = attendanceToday,
)

fun PaymentMethodDetailResponse.toDomain() = PaymentMethodDetail(
    method = method,
    orderCount = orderCount,
    revenue = revenue,
)

fun DailyRevenuePointResponse.toDomain() = DailyRevenuePoint(
    date = date,
    revenue = revenue,
)

fun RevenueProfitResponse.toDomain() = RevenueProfit(
    grossRevenue = grossRevenue,
    totalDeliveryFees = totalDeliveryFees,
    netRevenue = netRevenue,
    paymentMethods = paymentMethods.map { it.toDomain() },
    dailyTrend = dailyTrend.map { it.toDomain() },
)

fun DailyOrderTrendPointResponse.toDomain() = DailyOrderTrendPoint(
    date = date,
    total = total,
    completed = completed,
    cancelled = cancelled,
)

fun ChannelBreakdownResponse.toDomain() = ChannelBreakdown(
    channel = channel,
    count = count,
    percent = percent,
)

fun OrdersIntelligenceResponse.toDomain() = OrdersIntelligence(
    totalOrders = totalOrders,
    completedOrders = completedOrders,
    cancelledOrders = cancelledOrders,
    refundedOrders = refundedOrders,
    ordersByChannel = ordersByChannel,
    dailyTrend = dailyTrend.map { it.toDomain() },
    channelBreakdown = channelBreakdown.map { it.toDomain() },
)

fun HourlyDataResponse.toDomain() = HourlyData(
    hour = hour,
    orderCount = orderCount,
    revenue = revenue,
)

fun HeatmapPointResponse.toDomain() = HeatmapPoint(
    dayOfWeek = dayOfWeek,
    hour = hour,
    orderCount = orderCount,
)

fun DayOfWeekResponse.toDomain() = DayOfWeekData(
    dayOfWeek = dayOfWeek,
    name = name,
    orderCount = orderCount,
    revenue = revenue,
)

fun PeakTimeAnalysisResponse.toDomain() = PeakTimeAnalysis(
    busiestHour = busiestHour,
    busiestDay = busiestDay,
    hourlyData = hourlyData.map { it.toDomain() },
    heatmap = heatmap.map { it.toDomain() },
    dayOfWeek = dayOfWeek.map { it.toDomain() },
)

fun CashierPerformanceV2Response.toDomain() = CashierPerformanceV2(
    cashierId = cashierId,
    cashierName = cashierName,
    revenue = revenue,
    orderCount = orderCount,
    averageOrderValue = averageOrderValue,
    cancelledOrders = cancelledOrders,
    cancellationRate = cancellationRate,
)

fun DeliveryPerformanceV2Response.toDomain() = DeliveryPerformanceV2(
    driverId = driverId,
    driverName = driverName,
    ordersCompleted = ordersCompleted,
    feesCollected = feesCollected,
    revenue = revenue,
    avgDeliveryTimeMinutes = avgDeliveryTimeMinutes,
    lateDeliveryPercent = lateDeliveryPercent,
)

fun ProductItemResponse.toDomain() = ProductItem(
    itemId = itemId,
    itemName = itemName,
    categoryName = categoryName,
    quantitySold = quantitySold,
    revenue = revenue,
    costPrice = costPrice,
    profitMargin = profitMargin,
)

fun CategoryRevenueResponse.toDomain() = CategoryRevenue(
    categoryId = categoryId,
    categoryName = categoryName,
    revenue = revenue,
    itemCount = itemCount,
)

fun ProductIntelligenceResponse.toDomain() = ProductIntelligence(
    topSelling = topSelling.map { it.toDomain() },
    mostProfitable = mostProfitable.map { it.toDomain() },
    leastSelling = leastSelling.map { it.toDomain() },
    revenueByCategory = revenueByCategory.map { it.toDomain() },
    lowMarginWarnings = lowMarginWarnings.map { it.toDomain() },
)

fun TopCustomerResponse.toDomain() = TopCustomer(
    customerId = customerId,
    customerName = customerName,
    phone = phone,
    orderCount = orderCount,
    totalSpent = totalSpent,
)

fun CustomerIntelligenceResponse.toDomain() = CustomerIntelligence(
    totalCustomers = totalCustomers,
    newCustomersPercent = newCustomersPercent,
    returningCustomersPercent = returningCustomersPercent,
    averageSpend = averageSpend,
    lifetimeValue = lifetimeValue,
    topCustomers = topCustomers.map { it.toDomain() },
    frequencyBuckets = frequencyBuckets,
)

fun AlertResponse.toDomain() = AnalyticsAlert(
    type = type,
    severity = severity,
    title = title,
    message = message,
    value = value,
    threshold = threshold,
)

fun AlertsResponse.toDomain() = alerts.map { it.toDomain() }

fun StockOverviewItemResponse.toDomain() = StockOverviewItem(
    stockId = stockId,
    itemName = itemName,
    quantity = quantity,
    minQuantity = minQuantity,
    costPrice = costPrice,
    unit = unit,
    status = status,
)

fun StockMovementResponse.toDomain() = StockMovement(
    date = date,
    added = added,
    deducted = deducted,
)

fun StockOverviewResponse.toDomain() = StockOverview(
    totalStockValue = totalStockValue,
    totalSellingValue = totalSellingValue,
    potentialProfit = potentialProfit,
    totalItems = totalItems,
    lowStockItems = lowStockItems.map { it.toDomain() },
    outOfStockItems = outOfStockItems.map { it.toDomain() },
    deadStockItems = deadStockItems.map { it.toDomain() },
    movementSummary = movementSummary.map { it.toDomain() },
)

// ─── Worker Mappers ──────────────────────────────────────────────
fun WorkerResponse.toDomain() = Worker(
    id = id, vendorId = vendorId, workerId = workerId,
    fullName = fullName, phone = phone, description = description,
    role = role, salaryType = SalaryType.valueOf(salaryType),
    salaryAmount = salaryAmount, active = active,
    userId = userId, isLoginEnabled = isLoginEnabled,
    hasPin = hasPin, qrCodeVersion = qrCodeVersion, pinUpdatedAt = pinUpdatedAt,
    createdAt = createdAt, updatedAt = updatedAt
)

fun WorkerRoleResponse.toDomain() = WorkerRole(
    id = id, vendorId = vendorId, name = name,
    description = description, createdAt = createdAt
)

// ─── Attendance Mappers ──────────────────────────────────────────
fun AttendanceResponse.toDomain() = Attendance(
    id = id, vendorId = vendorId, workerId = workerId,
    workerName = workerName, workerRole = workerRole,
    date = date, checkIn = checkIn, checkOut = checkOut,
    workedMinutes = workedMinutes, recordedBy = recordedBy,
    authMethod = authMethod, note = note, createdAt = createdAt
)

fun AttendanceSummaryResponse.toDomain() = AttendanceSummary(
    workerId = workerId, workerName = workerName,
    workerRole = workerRole, totalDays = totalDays,
    totalWorkedMinutes = totalWorkedMinutes,
    presentToday = presentToday,
    attendedToday = attendedToday
)

// ─── Salary Payment Mappers ──────────────────────────────────────
fun SalaryPaymentResponse.toDomain() = SalaryPayment(
    id = id, vendorId = vendorId, workerId = workerId,
    workerName = workerName, periodType = periodType,
    periodStart = periodStart, periodEnd = periodEnd,
    workedDays = workedDays, workedHours = workedHours,
    amount = amount, paid = paid, paidAt = paidAt,
    paidBy = paidBy, note = note, createdAt = createdAt
)

// ─── Customer Mappers ──────────────────────────────────────────
fun CustomerResponse.toDomain() = Customer(
    id = id,
    vendorId = vendorId,
    name = name,
    phone = phone,
    notes = notes,
    orderCount = orderCount,
    totalSpent = totalSpent,
    lastOrderAt = lastOrderAt,
    addresses = addresses.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CustomerAddressResponse.toDomain() = CustomerAddress(
    id = id,
    customerId = customerId,
    label = label,
    address = address,
    geoLat = geoLat,
    geoLng = geoLng,
    deliveryZoneId = deliveryZoneId,
    deliveryFee = deliveryFee,
    isDefault = isDefault,
    createdAt = createdAt
)
