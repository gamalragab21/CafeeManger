package net.marllex.cafeemanger.core.database.mapper

import net.marllex.cafeemanger.core.model.*
import net.marllex.cafeemanger.core.database.entity.*

// ─── Vendor Mappers ──────────────────────────────────────────────
fun VendorEntity.toDomain() = Vendor(
    id = id, name = name, logoUrl = logoUrl, address = address,
    contactPhone = contactPhone, walletPhone = walletPhone,
    defaultDeliveryFee = defaultDeliveryFee,
    storeType = storeType, enableTables = enableTables,
    enableDineIn = enableDineIn, enableDelivery = enableDelivery,
    digitalMenuUrl = digitalMenuUrl,
    createdAt = createdAt, updatedAt = updatedAt
)

fun Vendor.toEntity() = VendorEntity(
    id = id, name = name, logoUrl = logoUrl, address = address,
    contactPhone = contactPhone, walletPhone = walletPhone,
    defaultDeliveryFee = defaultDeliveryFee,
    storeType = storeType, enableTables = enableTables,
    enableDineIn = enableDineIn, enableDelivery = enableDelivery,
    digitalMenuUrl = digitalMenuUrl,
    createdAt = createdAt, updatedAt = updatedAt
)

// ─── User Mappers ────────────────────────────────────────────────
fun UserEntity.toDomain() = User(
    id = id, vendorId = vendorId, role = UserRole.valueOf(role),
    name = name, phone = phone, email = email, active = active,
    createdAt = createdAt
)

fun User.toEntity() = UserEntity(
    id = id, vendorId = vendorId, role = role.name,
    name = name, phone = phone, email = email, active = active,
    createdAt = createdAt
)

// ─── Category Mappers ────────────────────────────────────────────
fun CategoryEntity.toDomain() = Category(
    id = id, vendorId = vendorId, name = name, displayOrder = displayOrder
)

fun Category.toEntity() = CategoryEntity(
    id = id, vendorId = vendorId, name = name, displayOrder = displayOrder
)

// ─── Item Mappers ────────────────────────────────────────────────
fun ItemEntity.toDomain() = Item(
    id = id, vendorId = vendorId, categoryId = categoryId,
    name = name, description = description, price = price,
    imageUrl = imageUrl, available = available
)

fun Item.toEntity() = ItemEntity(
    id = id, vendorId = vendorId, categoryId = categoryId,
    name = name, description = description, price = price,
    imageUrl = imageUrl, available = available
)

// ─── Table Mappers ───────────────────────────────────────────────
fun TableEntity.toDomain() = Table(
    id = id, vendorId = vendorId, number = number,
    capacity = capacity, status = TableStatus.valueOf(status)
)

fun Table.toEntity() = TableEntity(
    id = id, vendorId = vendorId, number = number,
    capacity = capacity, status = status.name
)

// ─── Order Mappers ───────────────────────────────────────────────
fun OrderEntity.toDomain(items: List<OrderItem> = emptyList()) = Order(
    id = id, vendorId = vendorId,
    channel = OrderChannel.valueOf(channel),
    status = OrderStatus.valueOf(status),
    tableId = tableId, cashierId = cashierId,
    cashierName = cashierName, deliveryUserId = deliveryUserId,
    deliveryUserName = deliveryUserName,
    clientName = clientName, clientPhone = clientPhone,
    clientAddress = clientAddress,
    geoLat = geoLat, geoLng = geoLng,
    paymentMethod = PaymentMethod.valueOf(paymentMethod),
    subtotal = subtotal, tax = tax, total = total,
    notes = notes, items = items,
    createdAt = createdAt, updatedAt = updatedAt
)

fun Order.toEntity() = OrderEntity(
    id = id, vendorId = vendorId, channel = channel.name,
    status = status.name, tableId = tableId, cashierId = cashierId,
    cashierName = cashierName, deliveryUserId = deliveryUserId,
    deliveryUserName = deliveryUserName,
    clientName = clientName, clientPhone = clientPhone,
    clientAddress = clientAddress,
    geoLat = geoLat, geoLng = geoLng,
    paymentMethod = paymentMethod.name,
    subtotal = subtotal, tax = tax, total = total,
    notes = notes, createdAt = createdAt, updatedAt = updatedAt
)

fun OrderItemEntity.toDomain() = OrderItem(
    id = id, orderId = orderId, itemId = itemId,
    itemNameSnapshot = itemNameSnapshot,
    itemPriceSnapshot = itemPriceSnapshot,
    quantity = quantity, note = note
)

fun OrderItem.toEntity() = OrderItemEntity(
    id = id, orderId = orderId, itemId = itemId,
    itemNameSnapshot = itemNameSnapshot,
    itemPriceSnapshot = itemPriceSnapshot,
    quantity = quantity, note = note
)

// ─── Stock Mappers ──────────────────────────────────────────────
fun StockEntity.toDomain() = Stock(
    id = id, vendorId = vendorId, itemId = itemId,
    itemName = itemName, quantity = quantity,
    minQuantity = minQuantity, costPrice = costPrice,
    unit = unit, isMenuItem = isMenuItem, alertEnabled = alertEnabled,
    lastUpdatedAt = lastUpdatedAt
)

fun Stock.toEntity() = StockEntity(
    id = id, vendorId = vendorId, itemId = itemId,
    itemName = itemName, quantity = quantity,
    minQuantity = minQuantity, costPrice = costPrice,
    unit = unit, isMenuItem = isMenuItem, alertEnabled = alertEnabled,
    lastUpdatedAt = lastUpdatedAt
)

fun StockTransactionEntity.toDomain() = StockTransaction(
    id = id, stockId = stockId, itemName = itemName,
    type = StockTransactionType.valueOf(type),
    quantity = quantity, previousQuantity = previousQuantity,
    orderId = orderId, note = note, createdAt = createdAt
)

fun StockTransaction.toEntity() = StockTransactionEntity(
    id = id, stockId = stockId, itemName = itemName,
    type = type.name,
    quantity = quantity, previousQuantity = previousQuantity,
    orderId = orderId, note = note, createdAt = createdAt
)

// ─── Worker Mappers ──────────────────────────────────────────────
fun WorkerEntity.toDomain() = Worker(
    id = id, vendorId = vendorId, workerId = workerId,
    fullName = fullName, phone = phone, description = description,
    role = role, salaryType = SalaryType.valueOf(salaryType),
    salaryAmount = salaryAmount, active = active,
    createdAt = createdAt, updatedAt = updatedAt
)

fun Worker.toEntity() = WorkerEntity(
    id = id, vendorId = vendorId, workerId = workerId,
    fullName = fullName, phone = phone, description = description,
    role = role, salaryType = salaryType.name,
    salaryAmount = salaryAmount, active = active,
    createdAt = createdAt, updatedAt = updatedAt
)

// ─── Worker Role Mappers ─────────────────────────────────────────
fun WorkerRoleEntity.toDomain() = WorkerRole(
    id = id, vendorId = vendorId, name = name,
    description = description, createdAt = createdAt
)

fun WorkerRole.toEntity() = WorkerRoleEntity(
    id = id, vendorId = vendorId, name = name,
    description = description, createdAt = createdAt
)

// ─── Attendance Mappers ──────────────────────────────────────────
fun AttendanceEntity.toDomain() = Attendance(
    id = id, vendorId = vendorId, workerId = workerId,
    workerName = workerName, workerRole = workerRole,
    date = date, checkIn = checkIn, checkOut = checkOut,
    workedMinutes = workedMinutes, recordedBy = recordedBy,
    note = note, createdAt = createdAt
)

fun Attendance.toEntity() = AttendanceEntity(
    id = id, vendorId = vendorId, workerId = workerId,
    workerName = workerName, workerRole = workerRole,
    date = date, checkIn = checkIn, checkOut = checkOut,
    workedMinutes = workedMinutes, recordedBy = recordedBy,
    note = note, createdAt = createdAt
)

// ─── Salary Payment Mappers ──────────────────────────────────────
fun SalaryPaymentEntity.toDomain() = SalaryPayment(
    id = id, vendorId = vendorId, workerId = workerId,
    workerName = workerName, periodType = periodType,
    periodStart = periodStart, periodEnd = periodEnd,
    workedDays = workedDays, workedHours = workedHours,
    amount = amount, paid = paid, paidAt = paidAt,
    paidBy = paidBy, note = note, createdAt = createdAt
)

fun SalaryPayment.toEntity() = SalaryPaymentEntity(
    id = id, vendorId = vendorId, workerId = workerId,
    workerName = workerName, periodType = periodType,
    periodStart = periodStart, periodEnd = periodEnd,
    workedDays = workedDays, workedHours = workedHours,
    amount = amount, paid = paid, paidAt = paidAt,
    paidBy = paidBy, note = note, createdAt = createdAt
)
