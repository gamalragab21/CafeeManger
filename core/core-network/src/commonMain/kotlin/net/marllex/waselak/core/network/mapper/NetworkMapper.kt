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
    enableKds = enableKds,
    enableDineIn = enableDineIn,
    enableDelivery = enableDelivery,
    enableTakeaway = enableTakeaway,
    enableInStore = enableInStore,
    enablePickupLater = enablePickupLater,
    businessType = businessType,
    taxEnabled = taxEnabled,
    defaultTaxPercent = defaultTaxPercent,
    stockMode = stockMode,
    offlineModeEnabled = offlineModeEnabled,
    biometricRequired = biometricRequired,
    enableOfflineMode = enableOfflineMode,
    digitalMenuUrl = digitalMenuUrl,
    enableDigitalMenu = enableDigitalMenu,
    enableRecipe = enableRecipe,
    enableSplitPayment = enableSplitPayment,
    enableCashDrawer = enableCashDrawer,
    enableReturns = enableReturns,
    enableCustomerCredit = enableCustomerCredit,
    enableInstallments = enableInstallments,
    enablePreOrders = enablePreOrders,
    enableScheduledOrders = enableScheduledOrders,
    enableSuppliers = enableSuppliers,
    enableDrugInteractions = enableDrugInteractions,
    enablePrescriptions = enablePrescriptions,
    enableAnalytics = enableAnalytics,
    enableAnnouncements = enableAnnouncements,
    loyaltyEnabled = loyaltyEnabled,
    pointsEarnRate = pointsEarnRate,
    pointsRedeemRate = pointsRedeemRate,
    minPointsRedeem = minPointsRedeem,
    maxManualDiscountPercent = maxManualDiscountPercent,
    manualDiscountRequiresPin = manualDiscountRequiresPin,
    facebookUrl = facebookUrl,
    landingPageUrl = landingPageUrl,
    instagramUrl = instagramUrl,
    whatsappNumber = whatsappNumber,
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
    photoUrl = photoUrl,
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
    variantGroups = variantGroups.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun VariantGroupResponse.toDomain() = VariantGroup(
    id = id,
    name = name,
    required = required,
    displayOrder = displayOrder,
    options = options.map { it.toDomain() }
)

fun VariantOptionResponse.toDomain() = VariantOption(
    id = id,
    name = name,
    priceAdjustment = priceAdjustment,
    isDefault = isDefault,
    displayOrder = displayOrder
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

// ─── Reservation Mappers ────────────────────────────────────────
fun ReservationResponse.toDomain() = Reservation(
    id = id,
    vendorId = vendorId,
    tableId = tableId,
    tableNumber = tableNumber,
    clientName = clientName,
    clientPhone = clientPhone,
    reservationDate = reservationDate,
    reservationTime = reservationTime,
    numberOfGuests = numberOfGuests,
    notes = notes,
    status = ReservationStatus.valueOf(status),
    orderId = orderId,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ─── Offer Mappers ──────────────────────────────────────────────
fun OfferResponse.toDomain() = Offer(
    id = id,
    vendorId = vendorId,
    name = name,
    description = description,
    imageUrl = imageUrl,
    discountType = discountType,
    discountValue = discountValue,
    active = active,
    expiresAt = expiresAt,
    promoCode = promoCode,
    maxUses = maxUses,
    usedCount = usedCount,
    startsAt = startsAt,
    displayOrder = displayOrder,
    items = items.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun OfferItemResponse.toDomain() = OfferItem(
    id = id,
    offerId = offerId,
    itemId = itemId,
    itemName = itemName,
    itemPrice = itemPrice,
    quantity = quantity,
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
    offerId = offerId,
    items = items.map { it.toDomain() },
    pointsEarned = pointsEarned,
    pointsRedeemed = pointsRedeemed,
    discountReason = discountReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    refundedAt = refundedAt,
    refundedBy = refundedBy,
    refundReason = refundReason,
    refundedAmount = refundedAmount,
    returnedItemCount = returnedItemCount,
    doctorName = doctorName,
    diagnosis = diagnosis,
)

fun OrderItemResponse.toDomain() = OrderItem(
    id = id,
    orderId = orderId,
    itemId = itemId,
    itemNameSnapshot = itemNameSnapshot,
    itemPriceSnapshot = itemPriceSnapshot,
    quantity = quantity,
    note = note,
    variantOptionsSnapshot = variantOptionsSnapshot
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
    status = status,
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
    fixedQuantity = fixedQuantity,
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

// ─── Offers Analytics Mappers ───────────────────────────────────

fun OfferPerformanceItemResponse.toDomain() = OfferPerformanceItem(
    offerId = offerId,
    offerName = offerName,
    discountType = discountType,
    discountValue = discountValue,
    usageCount = usageCount,
    totalDiscountGiven = totalDiscountGiven,
    totalRevenueFromOfferOrders = totalRevenueFromOfferOrders,
    promoCode = promoCode,
    isActive = isActive,
)

fun DailyOfferUsageResponse.toDomain() = DailyOfferUsage(
    date = date,
    usageCount = usageCount,
    discountAmount = discountAmount,
)

fun OffersAnalyticsResponse.toDomain() = OffersAnalytics(
    totalOffers = totalOffers,
    activeOffers = activeOffers,
    totalOfferUses = totalOfferUses,
    totalDiscountFromOffers = totalDiscountFromOffers,
    averageDiscountPerUse = averageDiscountPerUse,
    topOffers = topOffers.map { it.toDomain() },
    offerUsageTrend = offerUsageTrend.map { it.toDomain() },
)

// ─── Discount Analytics Mappers ─────────────────────────────────

fun DiscountBreakdownResponse.toDomain() = DiscountBreakdown(
    type = type,
    count = count,
    totalAmount = totalAmount,
    percentOfTotal = percentOfTotal,
)

fun DailyDiscountResponse.toDomain() = DailyDiscount(
    date = date,
    manualDiscount = manualDiscount,
    offerDiscount = offerDiscount,
    pointsDiscount = pointsDiscount,
)

fun DiscountAnalyticsResponse.toDomain() = DiscountAnalytics(
    totalOrdersWithDiscount = totalOrdersWithDiscount,
    totalDiscountGiven = totalDiscountGiven,
    averageDiscountPerOrder = averageDiscountPerOrder,
    discountRate = discountRate,
    breakdown = breakdown.map { it.toDomain() },
    dailyTrend = dailyTrend.map { it.toDomain() },
)

// ─── Loyalty Analytics Mappers ──────────────────────────────────

fun DailyLoyaltyResponse.toDomain() = DailyLoyalty(
    date = date,
    pointsEarned = pointsEarned,
    pointsRedeemed = pointsRedeemed,
)

fun LoyaltyAnalyticsResponse.toDomain() = LoyaltyAnalytics(
    totalPointsEarned = totalPointsEarned,
    totalPointsRedeemed = totalPointsRedeemed,
    totalPointsOutstanding = totalPointsOutstanding,
    activeLoyaltyCustomers = activeLoyaltyCustomers,
    redemptionRate = redemptionRate,
    pointsToRevenue = pointsToRevenue,
    dailyTrend = dailyTrend.map { it.toDomain() },
)

// ─── Supplier Analytics Mappers ─────────────────────────────────

fun TopSupplierResponse.toDomain() = TopSupplier(
    supplierId = supplierId,
    supplierName = supplierName,
    totalOrders = totalOrders,
    totalSpent = totalSpent,
    receivedOrders = receivedOrders,
    pendingOrders = pendingOrders,
)

fun SupplierItemResponse.toDomain() = SupplierItem(
    stockId = stockId,
    itemName = itemName,
    totalQuantity = totalQuantity,
    totalCost = totalCost,
    orderCount = orderCount,
    unit = unit,
)

fun MonthlyPurchaseResponse.toDomain() = MonthlyPurchase(
    month = month,
    total = total,
    orderCount = orderCount,
)

fun SupplierAnalyticsResponse.toDomain() = SupplierAnalytics(
    totalSuppliers = totalSuppliers,
    activeSuppliers = activeSuppliers,
    totalPurchaseOrders = totalPurchaseOrders,
    totalSpent = totalSpent,
    pendingOrders = pendingOrders,
    receivedOrders = receivedOrders,
    averageOrderValue = averageOrderValue,
    topSuppliers = topSuppliers.map { it.toDomain() },
    topItems = topItems.map { it.toDomain() },
    monthlyTrend = monthlyTrend.map { it.toDomain() },
)

// ─── Staff Costs Analytics Mappers ──────────────────────────────

fun WorkerOvertimeSummaryResponse.toDomain() = WorkerOvertimeSummary(
    workerId = workerId,
    workerName = workerName,
    overtimeHours = overtimeHours,
    overtimeAmount = overtimeAmount,
)

fun StaffCostsAnalyticsResponse.toDomain() = StaffCostsAnalytics(
    totalSalaries = totalSalaries,
    totalOvertime = totalOvertime,
    totalCompensation = totalCompensation,
    paidAmount = paidAmount,
    unpaidAmount = unpaidAmount,
    overtimeHours = overtimeHours,
    workersCount = workersCount,
    overtimePercentage = overtimePercentage,
    topOvertimeWorkers = topOvertimeWorkers.map { it.toDomain() },
)

// ─── Worker Mappers ──────────────────────────────────────────────
fun WorkerResponse.toDomain() = Worker(
    id = id, vendorId = vendorId, workerId = workerId,
    fullName = fullName, phone = phone, description = description,
    photoUrl = photoUrl,
    role = role, salaryType = SalaryType.valueOf(salaryType),
    salaryAmount = salaryAmount, active = active,
    userId = userId, isLoginEnabled = isLoginEnabled,
    hasPin = hasPin, pinSha256 = pinSha256, qrCodeVersion = qrCodeVersion, pinUpdatedAt = pinUpdatedAt,
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
    amount = amount, overtimeHours = overtimeHours,
    overtimeAmount = overtimeAmount, paid = paid, paidAt = paidAt,
    paidBy = paidBy, note = note, createdAt = createdAt
)

// ─── Overtime Mappers ────────────────────────────────────────────
fun OvertimeResponse.toDomain() = Overtime(
    id = id, vendorId = vendorId, workerId = workerId,
    workerName = workerName, date = date, hours = hours,
    ratePerHour = ratePerHour, amount = amount, note = note,
    paid = paid, createdBy = createdBy, createdAt = createdAt
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
    pointsBalance = pointsBalance,
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

// ─── KDS Mappers ────────────────────────────────────────────────
fun KdsOrderResponse.toDomain() = KdsOrder(
    orderId = orderId,
    orderNumber = orderNumber,
    channel = channel,
    tableNumber = tableNumber,
    clientName = clientName,
    notes = notes,
    items = items.map { it.toDomain() },
    createdAt = createdAt,
    elapsedMinutes = elapsedMinutes,
)

fun KdsOrderItemResponse.toDomain() = KdsOrderItem(
    id = id,
    orderId = orderId,
    itemName = itemName,
    quantity = quantity,
    note = note,
    variantOptions = variantOptions,
    kitchenStatus = kitchenStatus,
    kitchenStation = kitchenStation,
    createdAt = createdAt,
)

fun KdsSummaryResponse.toDomain() = KdsSummary(
    totalItems = totalItems,
    pending = pending,
    cooking = cooking,
    ready = ready,
    served = served,
    avgPrepTimeMinutes = avgPrepTimeMinutes,
)

// ─── Cash Drawer Mappers ────────────────────────────────────────
fun CashDrawerSessionResponse.toDomain() = CashDrawerSession(
    id = id,
    vendorId = vendorId,
    cashierId = cashierId,
    cashierName = cashierName,
    openedAt = openedAt,
    closedAt = closedAt,
    openingBalance = openingBalance,
    closingBalance = closingBalance,
    expectedBalance = expectedBalance,
    difference = difference,
    status = status,
    notes = notes,
    movements = movements.map { it.toDomain() },
    createdAt = createdAt,
)

fun CashMovementResponse.toDomain() = CashMovement(
    id = id,
    sessionId = sessionId,
    vendorId = vendorId,
    type = type,
    amount = amount,
    reason = reason,
    orderId = orderId,
    createdBy = createdBy,
    createdByName = createdByName,
    createdAt = createdAt,
)

fun DrawerSummaryResponse.toDomain() = DrawerSummary(
    sessionId = sessionId,
    openingBalance = openingBalance,
    totalCashIn = totalCashIn,
    totalCashOut = totalCashOut,
    totalSales = totalSales,
    totalRefunds = totalRefunds,
    expectedBalance = expectedBalance,
    movementCount = movementCount,
    totalOrders = totalOrders,
    cashSales = cashSales,
    cardSales = cardSales,
    walletSales = walletSales,
    creditSales = creditSales,
    cashOrderCount = cashOrderCount,
    cardOrderCount = cardOrderCount,
    walletOrderCount = walletOrderCount,
    creditOrderCount = creditOrderCount,
    installmentPayments = installmentPayments,
    installmentPaymentCount = installmentPaymentCount,
    channels = channels.map { ChannelSummary(channel = it.channel, orderCount = it.orderCount, totalAmount = it.totalAmount) },
)

// ─── Split Payment Mappers ──────────────────────────────────────
fun OrderPaymentResponse.toDomain() = OrderPayment(
    id = id,
    orderId = orderId,
    vendorId = vendorId,
    paymentMethod = paymentMethod,
    amount = amount,
    paidBy = paidBy,
    paidByName = paidByName,
    note = note,
    createdAt = createdAt,
)

fun AddPaymentResponse.toDomain() = payment.toDomain()

fun SplitPaymentSummaryResponse.toDomain() = SplitPaymentSummary(
    orderId = orderId,
    orderTotal = orderTotal,
    totalPaid = totalPaid,
    remaining = remaining,
    isFullyPaid = isFullyPaid,
    payments = payments.map { it.toDomain() },
)

// ─── Prescription Mappers ───────────────────────────────────────
fun PrescriptionResponse.toDomain() = Prescription(
    id = id,
    vendorId = vendorId,
    customerId = customerId,
    orderId = orderId,
    doctorName = doctorName,
    doctorPhone = doctorPhone,
    patientName = patientName,
    patientPhone = patientPhone,
    patientAge = patientAge,
    diagnosis = diagnosis,
    notes = notes,
    imageUrl = imageUrl,
    status = status,
    expiresAt = expiresAt,
    dispensedAt = dispensedAt,
    dispensedBy = dispensedBy,
    createdBy = createdBy,
    items = items.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PrescriptionItemResponse.toDomain() = PrescriptionItem(
    id = id,
    prescriptionId = prescriptionId,
    itemId = itemId,
    itemName = itemName,
    quantity = quantity,
    dosage = dosage,
    frequency = frequency,
    duration = duration,
    instructions = instructions,
    dispensedQuantity = dispensedQuantity,
    status = status,
    substituteItemId = substituteItemId,
    substituteItemName = substituteItemName,
    createdAt = createdAt,
)

// ─── Drug Interaction Mappers ───────────────────────────────────
fun DrugInteractionResponse.toDomain() = DrugInteraction(
    id = id,
    vendorId = vendorId,
    itemIdA = itemIdA,
    itemNameA = itemNameA,
    itemIdB = itemIdB,
    itemNameB = itemNameB,
    severity = severity,
    description = description,
    descriptionAr = descriptionAr,
    recommendation = recommendation,
    active = active,
    createdAt = createdAt,
)

fun InteractionCheckResultResponse.toDomain() = InteractionCheckResult(
    hasInteractions = hasInteractions,
    interactions = interactions.map { it.toDomain() },
)

// ─── Customer Credit Mappers ────────────────────────────────────
fun CustomerCreditResponse.toDomain() = CustomerCredit(
    id = id,
    vendorId = vendorId,
    customerId = customerId,
    customerName = customerName,
    customerPhone = customerPhone,
    balance = balance,
    creditLimit = creditLimit,
    availableCredit = availableCredit,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CreditTransactionResponse.toDomain() = CreditTransaction(
    id = id,
    creditId = creditId,
    vendorId = vendorId,
    orderId = orderId,
    type = type,
    amount = amount,
    previousBalance = previousBalance,
    newBalance = newBalance,
    note = note,
    createdBy = createdBy,
    createdByName = createdByName,
    createdAt = createdAt,
)

// ─── Scheduled Order Mappers ────────────────────────────────────
fun ScheduledOrderResponse.toDomain() = ScheduledOrder(
    id = id,
    vendorId = vendorId,
    customerId = customerId,
    clientName = clientName,
    clientPhone = clientPhone,
    channel = channel,
    scheduledFor = scheduledFor,
    reminderSentAt = reminderSentAt,
    status = status,
    notes = notes,
    paymentMethod = paymentMethod,
    paymentStatus = paymentStatus,
    subtotal = subtotal,
    total = total,
    discount = discount,
    tax = tax,
    deliveryFee = deliveryFee,
    orderId = orderId,
    createdBy = createdBy,
    items = items.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ScheduledOrderItemResponse.toDomain() = ScheduledOrderItem(
    id = id,
    scheduledOrderId = scheduledOrderId,
    itemId = itemId,
    itemName = itemName,
    itemPrice = itemPrice,
    quantity = quantity,
    note = note,
    variantOptions = variantOptions,
    createdAt = createdAt,
)

// ─── Supplier Mappers ───────────────────────────────────────────
fun SupplierResponse.toDomain() = Supplier(
    id = id,
    vendorId = vendorId,
    name = name,
    contactName = contactName,
    phone = phone,
    email = email,
    address = address,
    notes = notes,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PurchaseOrderResponse.toDomain() = PurchaseOrder(
    id = id,
    vendorId = vendorId,
    supplierId = supplierId,
    supplierName = supplierName,
    orderNumber = orderNumber,
    status = status,
    notes = notes,
    subtotal = subtotal,
    tax = tax,
    total = total,
    expectedDeliveryDate = expectedDeliveryDate,
    receivedAt = receivedAt,
    createdBy = createdBy,
    items = items.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PurchaseOrderItemResponse.toDomain() = PurchaseOrderItem(
    id = id,
    purchaseOrderId = purchaseOrderId,
    stockId = stockId,
    stockName = stockName,
    requestedQuantity = requestedQuantity,
    receivedQuantity = receivedQuantity,
    unitCost = unitCost,
    totalCost = totalCost,
    unit = unit,
    notes = notes,
    createdAt = createdAt,
)

// ─── Returns Mappers ────────────────────────────────────────────
fun ProductReturnResponse.toDomain() = ProductReturn(
    id = id,
    vendorId = vendorId,
    orderId = orderId,
    customerId = customerId,
    returnType = returnType,
    status = status,
    reason = reason,
    refundAmount = refundAmount,
    refundMethod = refundMethod,
    processedBy = processedBy,
    processedAt = processedAt,
    notes = notes,
    items = items.map { it.toDomain() },
    exchangeItemId = exchangeItemId,
    exchangeItemName = exchangeItemName,
    exchangeItemPrice = exchangeItemPrice,
    exchangeQuantity = exchangeQuantity,
    createdAt = createdAt,
)

fun ReturnItemResponse.toDomain() = ReturnItem(
    id = id,
    returnId = returnId,
    orderItemId = orderItemId,
    itemId = itemId,
    itemName = itemName,
    quantity = quantity,
    reason = reason,
    itemCondition = itemCondition,
    restockable = restockable,
    refundAmount = refundAmount,
    createdAt = createdAt,
)

fun ReturnsSummaryResponse.toDomain() = ReturnsSummary(
    total = total,
    pending = pending,
    completed = completed,
    rejected = rejected,
    totalRefunded = totalRefunded,
)

// ─── Notification Mappers ───────────────────────────────────────
fun NotificationResponse.toDomain() = AppNotification(
    id = id,
    vendorId = vendorId,
    userId = userId,
    type = type,
    title = title,
    body = body,
    data = data,
    channel = channel,
    priority = priority,
    read = read,
    readAt = readAt,
    actionUrl = actionUrl,
    platform = platform,
    createdAt = createdAt,
)

fun NotificationCountResponse.toDomain() = NotificationCount(
    total = total,
    unread = unread,
)

fun DeviceTokenResponse.toDomain() = DeviceToken(
    id = id,
    userId = userId,
    vendorId = vendorId,
    token = token,
    platform = platform,
    deviceName = deviceName,
    active = active,
    lastUsedAt = lastUsedAt,
    createdAt = createdAt,
)

// ─── Credit Analytics Mappers ──────────────────────────────────
fun CreditAnalyticsResponse.toDomain() = CreditAnalytics(
    totalOutstanding = totalOutstanding,
    totalCreditLimit = totalCreditLimit,
    utilizationPercent = utilizationPercent,
    totalCreditCustomers = totalCreditCustomers,
    totalCharges = totalCharges,
    totalPayments = totalPayments,
    creditOrdersCount = creditOrdersCount,
    creditOrdersRevenue = creditOrdersRevenue,
    topDebtors = topDebtors.map { it.toDomain() },
)

fun CreditDebtorResponse.toDomain() = CreditDebtor(
    customerName = customerName,
    customerPhone = customerPhone,
    balance = balance,
    creditLimit = creditLimit,
)

fun DoctorStatsResponse.toDomain() = DoctorStats(
    doctorName = doctorName,
    prescriptionCount = prescriptionCount,
    totalItems = totalItems,
    totalRevenue = totalRevenue,
)

// ─── Returns Analytics Mappers ──────────────────────────────────
// ─── Installments ────────────────────────────────────────────────

fun InstallmentPlanResponse.toDomain() = InstallmentPlan(
    id = id, vendorId = vendorId, customerId = customerId,
    customerName = customerName, customerPhone = customerPhone,
    orderId = orderId, totalAmount = totalAmount, downPayment = downPayment,
    remainingAmount = remainingAmount, numInstallments = numInstallments,
    installmentAmount = installmentAmount, lateFeePercent = lateFeePercent,
    status = status, startDate = startDate,
    payments = payments.map { it.toDomain() },
    createdBy = createdBy, createdByName = createdByName,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun InstallmentPaymentResponse.toDomain() = InstallmentPayment(
    id = id, planId = planId, dueDate = dueDate,
    amount = amount, paidAmount = paidAmount, lateFee = lateFee,
    status = status, paidAt = paidAt, paidBy = paidBy,
    paidByName = paidByName, note = note,
    lateFeeEnabled = lateFeeEnabled, createdAt = createdAt,
)

fun InstallmentAnalyticsResponse.toDomain() = InstallmentAnalytics(
    totalPlans = totalPlans, activePlans = activePlans,
    completedPlans = completedPlans, defaultedPlans = defaultedPlans,
    totalRevenue = totalRevenue, collectedRevenue = collectedRevenue,
    pendingRevenue = pendingRevenue, overdueRevenue = overdueRevenue,
    lateFeesCollected = lateFeesCollected,
)

fun ReturnsAnalyticsResponse.toDomain() = ReturnsAnalytics(
    totalReturns = totalReturns,
    totalRefunds = totalRefunds,
    totalExchanges = totalExchanges,
    totalRefundedAmount = totalRefundedAmount,
    totalExchangedAmount = totalExchangedAmount,
    totalReturnedItems = totalReturnedItems,
    returnedItemsBreakdown = returnedItemsBreakdown.map { ReturnedItemBreakdown(itemName = it.itemName, totalQuantity = it.totalQuantity, totalAmount = it.totalAmount) },
    exchangeItems = exchangeItems.map { ExchangeItemBreakdown(itemName = it.itemName, quantity = it.quantity, price = it.price) },
)
