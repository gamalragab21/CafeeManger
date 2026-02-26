package net.marllex.waselak.core.database.mapper

import net.marllex.waselak.core.model.*
import net.marllex.waselak.core.database.Vendors
import net.marllex.waselak.core.database.Users
import net.marllex.waselak.core.database.Categories
import net.marllex.waselak.core.database.Items
import net.marllex.waselak.core.database.Tables
import net.marllex.waselak.core.database.Orders
import net.marllex.waselak.core.database.Order_items
import net.marllex.waselak.core.database.Stock
import net.marllex.waselak.core.database.Stock_transactions
import net.marllex.waselak.core.database.Recipes
import net.marllex.waselak.core.database.Recipe_ingredients
import net.marllex.waselak.core.database.Workers
import net.marllex.waselak.core.database.Worker_roles
import net.marllex.waselak.core.database.Attendance as AttendanceDb
import net.marllex.waselak.core.database.Salary_payments
import net.marllex.waselak.core.database.Customers
import net.marllex.waselak.core.database.Customer_addresses
import net.marllex.waselak.core.database.Overtime_entries

// ─── Vendor Mappers ──────────────────────────────────────────────
fun Vendors.toDomain() = Vendor(
    id = id, name = name, logoUrl = logo_url, address = address,
    contactPhone = contact_phone, walletPhone = wallet_phone,
    defaultDeliveryFee = default_delivery_fee,
    storeType = store_type, enableTables = enable_tables,
    enableDineIn = enable_dine_in, enableDelivery = enable_delivery,
    enableTakeaway = enable_takeaway,
    enableInStore = enable_in_store,
    enablePickupLater = enable_pickup_later,
    businessType = business_type,
    taxEnabled = tax_enabled,
    defaultTaxPercent = default_tax_percent,
    stockMode = stock_mode,
    offlineModeEnabled = offline_mode_enabled,
    biometricRequired = biometric_required,
    enableOfflineMode = enable_offline_mode,
    digitalMenuUrl = digital_menu_url,
    createdAt = created_at, updatedAt = updated_at
)

fun Vendor.toDbEntity() = Vendors(
    id = id, name = name, logo_url = logoUrl, address = address,
    contact_phone = contactPhone, wallet_phone = walletPhone,
    default_delivery_fee = defaultDeliveryFee,
    store_type = storeType, enable_tables = enableTables,
    enable_dine_in = enableDineIn, enable_delivery = enableDelivery,
    enable_takeaway = enableTakeaway,
    enable_in_store = enableInStore,
    enable_pickup_later = enablePickupLater,
    business_type = businessType,
    tax_enabled = taxEnabled,
    default_tax_percent = defaultTaxPercent,
    stock_mode = stockMode,
    offline_mode_enabled = offlineModeEnabled,
    biometric_required = biometricRequired,
    enable_offline_mode = enableOfflineMode,
    digital_menu_url = digitalMenuUrl,
    created_at = createdAt, updated_at = updatedAt
)

// ─── User Mappers ────────────────────────────────────────────────
fun Users.toDomain() = User(
    id = id, vendorId = vendor_id, role = UserRole.valueOf(role),
    name = name, phone = phone, email = email, active = active,
    createdAt = created_at
)

fun User.toDbEntity() = Users(
    id = id, vendor_id = vendorId, role = role.name,
    name = name, phone = phone, email = email, active = active,
    created_at = createdAt
)

// ─── Category Mappers ────────────────────────────────────────────
fun Categories.toDomain() = Category(
    id = id, vendorId = vendor_id, name = name, displayOrder = display_order
)

fun Category.toDbEntity() = Categories(
    id = id, vendor_id = vendorId, name = name, display_order = displayOrder
)

// ─── Item Mappers ────────────────────────────────────────────────
fun Items.toDomain() = Item(
    id = id, vendorId = vendor_id, categoryId = category_id,
    name = name, description = description, price = price,
    costPrice = cost_price, sku = sku, barcode = barcode,
    imageUrl = image_url, available = available,
    stockBehavior = stock_behavior
)

fun Item.toDbEntity() = Items(
    id = id, vendor_id = vendorId, category_id = categoryId,
    name = name, description = description, price = price,
    cost_price = costPrice, sku = sku, barcode = barcode,
    image_url = imageUrl, available = available,
    stock_behavior = stockBehavior
)

// ─── Table Mappers ───────────────────────────────────────────────
fun Tables.toDomain() = Table(
    id = id, vendorId = vendor_id, number = number,
    capacity = capacity, status = TableStatus.valueOf(status)
)

fun Table.toDbEntity() = Tables(
    id = id, vendor_id = vendorId, number = number,
    capacity = capacity, status = status.name
)

// ─── Order Mappers ───────────────────────────────────────────────
fun Orders.toDomain(items: List<OrderItem> = emptyList()) = Order(
    id = id, vendorId = vendor_id,
    channel = OrderChannel.valueOf(channel),
    status = OrderStatus.parse(status),
    tableId = table_id, tableNumber = table_number,
    cashierId = cashier_id,
    cashierName = cashier_name, deliveryUserId = delivery_user_id,
    deliveryUserName = delivery_user_name,
    clientName = client_name, clientPhone = client_phone,
    clientAddress = client_address, customerId = customer_id,
    geoLat = geo_lat, geoLng = geo_lng,
    paymentMethod = PaymentMethod.valueOf(payment_method),
    paymentStatus = PaymentStatus.valueOf(payment_status),
    paymentTiming = PaymentTiming.valueOf(payment_timing),
    paymentConfirmedAt = payment_confirmed_at,
    paymentConfirmedBy = payment_confirmed_by,
    subtotal = subtotal, deliveryFee = delivery_fee,
    discount = discount, discountType = discount_type,
    tax = tax, taxPercent = tax_percent, total = total,
    notes = notes, items = items,
    createdAt = created_at, updatedAt = updated_at,
    refundedAt = refunded_at, refundedBy = refunded_by, refundReason = refund_reason,
    syncStatus = sync_status,
)

fun Order.toDbEntity() = Orders(
    id = id, vendor_id = vendorId, channel = channel.name,
    status = status.name, table_id = tableId, table_number = tableNumber,
    cashier_id = cashierId,
    cashier_name = cashierName, delivery_user_id = deliveryUserId,
    delivery_user_name = deliveryUserName,
    client_name = clientName, client_phone = clientPhone,
    client_address = clientAddress, customer_id = customerId,
    geo_lat = geoLat, geo_lng = geoLng,
    payment_method = paymentMethod.name,
    payment_status = paymentStatus.name,
    payment_timing = paymentTiming.name,
    payment_confirmed_at = paymentConfirmedAt,
    payment_confirmed_by = paymentConfirmedBy,
    subtotal = subtotal, delivery_fee = deliveryFee,
    discount = discount, discount_type = discountType,
    tax = tax, tax_percent = taxPercent, total = total,
    notes = notes, created_at = createdAt, updated_at = updatedAt,
    refunded_at = refundedAt, refunded_by = refundedBy, refund_reason = refundReason,
    sync_status = syncStatus,
)

fun Order_items.toDomain() = OrderItem(
    id = id, orderId = order_id, itemId = item_id,
    itemNameSnapshot = item_name_snapshot,
    itemPriceSnapshot = item_price_snapshot,
    quantity = quantity, note = note
)

fun OrderItem.toDbEntity() = Order_items(
    id = id, order_id = orderId, item_id = itemId,
    item_name_snapshot = itemNameSnapshot,
    item_price_snapshot = itemPriceSnapshot,
    quantity = quantity, note = note
)

// ─── Stock Mappers ──────────────────────────────────────────────
fun Stock.toDomain() = net.marllex.waselak.core.model.Stock(
    id = id, vendorId = vendor_id, itemId = item_id,
    itemName = item_name, quantity = quantity,
    minQuantity = min_quantity, costPrice = cost_price,
    unit = unit, baseUnit = base_unit, conversionRate = conversion_rate,
    isMenuItem = is_menu_item, alertEnabled = alert_enabled,
    lastUpdatedAt = last_updated_at
)

fun net.marllex.waselak.core.model.Stock.toDbEntity() = Stock(
    id = id, vendor_id = vendorId, item_id = itemId,
    item_name = itemName, quantity = quantity,
    min_quantity = minQuantity, cost_price = costPrice,
    unit = unit, base_unit = baseUnit, conversion_rate = conversionRate,
    is_menu_item = isMenuItem, alert_enabled = alertEnabled,
    last_updated_at = lastUpdatedAt
)

fun Stock_transactions.toDomain() = StockTransaction(
    id = id, stockId = stock_id, itemName = item_name,
    type = try { StockTransactionType.valueOf(type) } catch (_: Exception) { StockTransactionType.ADJUST },
    quantity = quantity, previousQuantity = previous_quantity,
    orderId = order_id, recipeId = recipe_id, note = note, createdAt = created_at
)

fun StockTransaction.toDbEntity() = Stock_transactions(
    id = id, stock_id = stockId, item_name = itemName,
    type = type.name,
    quantity = quantity, previous_quantity = previousQuantity,
    order_id = orderId, recipe_id = recipeId, note = note, created_at = createdAt
)

// ─── Worker Mappers ──────────────────────────────────────────────
fun Workers.toDomain() = Worker(
    id = id, vendorId = vendor_id, workerId = worker_id,
    fullName = full_name, phone = phone, description = description,
    role = role, salaryType = SalaryType.valueOf(salary_type),
    salaryAmount = salary_amount, active = active,
    userId = user_id, isLoginEnabled = is_login_enabled,
    hasPin = has_pin, pinSha256 = pin_sha256, qrCodeVersion = qr_code_version, pinUpdatedAt = pin_updated_at,
    createdAt = created_at, updatedAt = updated_at
)

fun Worker.toDbEntity() = Workers(
    id = id, vendor_id = vendorId, worker_id = workerId,
    full_name = fullName, phone = phone, description = description,
    role = role, salary_type = salaryType.name,
    salary_amount = salaryAmount, active = active,
    user_id = userId, is_login_enabled = isLoginEnabled,
    has_pin = hasPin, pin_sha256 = pinSha256, qr_code_version = qrCodeVersion, pin_updated_at = pinUpdatedAt,
    created_at = createdAt, updated_at = updatedAt
)

// ─── Worker Role Mappers ─────────────────────────────────────────
fun Worker_roles.toDomain() = WorkerRole(
    id = id, vendorId = vendor_id, name = name,
    description = description, createdAt = created_at
)

fun WorkerRole.toDbEntity() = Worker_roles(
    id = id, vendor_id = vendorId, name = name,
    description = description, created_at = createdAt
)

// ─── Attendance Mappers ──────────────────────────────────────────
fun AttendanceDb.toDomain() = net.marllex.waselak.core.model.Attendance(
    id = id, vendorId = vendor_id, workerId = worker_id,
    workerName = worker_name, workerRole = worker_role,
    date = date, checkIn = check_in, checkOut = check_out,
    workedMinutes = worked_minutes, recordedBy = recorded_by,
    authMethod = auth_method, note = note, createdAt = created_at,
    syncStatus = sync_status,
)

fun net.marllex.waselak.core.model.Attendance.toDbEntity() = AttendanceDb(
    id = id, vendor_id = vendorId, worker_id = workerId,
    worker_name = workerName, worker_role = workerRole,
    date = date, check_in = checkIn, check_out = checkOut,
    worked_minutes = workedMinutes, recorded_by = recordedBy,
    auth_method = authMethod, note = note, created_at = createdAt,
    sync_status = syncStatus,
)

// ─── Salary Payment Mappers ──────────────────────────────────────
fun Salary_payments.toDomain() = SalaryPayment(
    id = id, vendorId = vendor_id, workerId = worker_id,
    workerName = worker_name, periodType = period_type,
    periodStart = period_start, periodEnd = period_end,
    workedDays = worked_days, workedHours = worked_hours,
    amount = amount, paid = paid, paidAt = paid_at,
    paidBy = paid_by, note = note, createdAt = created_at
)

fun SalaryPayment.toDbEntity() = Salary_payments(
    id = id, vendor_id = vendorId, worker_id = workerId,
    worker_name = workerName, period_type = periodType,
    period_start = periodStart, period_end = periodEnd,
    worked_days = workedDays, worked_hours = workedHours,
    amount = amount, paid = paid, paid_at = paidAt,
    paid_by = paidBy, note = note, created_at = createdAt
)

// ─── Overtime Mappers ────────────────────────────────────────────
fun Overtime_entries.toDomain() = Overtime(
    id = id, vendorId = vendor_id, workerId = worker_id,
    workerName = worker_name, date = date, hours = hours,
    ratePerHour = rate_per_hour, amount = amount, note = note,
    createdBy = created_by, createdAt = created_at
)

fun Overtime.toDbEntity() = Overtime_entries(
    id = id, vendor_id = vendorId, worker_id = workerId,
    worker_name = workerName, date = date, hours = hours,
    rate_per_hour = ratePerHour, amount = amount, note = note,
    created_by = createdBy, created_at = createdAt
)

// ─── Customer Mappers ───────────────────────────────────────────
fun Customers.toDomain(addresses: List<CustomerAddress> = emptyList()) = Customer(
    id = id, vendorId = vendor_id, name = name, phone = phone,
    notes = notes, orderCount = order_count, totalSpent = total_spent,
    lastOrderAt = last_order_at, addresses = addresses,
    createdAt = created_at, updatedAt = updated_at
)

fun Customer.toDbEntity() = Customers(
    id = id, vendor_id = vendorId, name = name, phone = phone,
    notes = notes, order_count = orderCount, total_spent = totalSpent,
    last_order_at = lastOrderAt,
    created_at = createdAt, updated_at = updatedAt
)

// ─── Customer Address Mappers ───────────────────────────────────
fun Customer_addresses.toDomain() = CustomerAddress(
    id = id, customerId = customer_id, label = label,
    address = address, geoLat = geo_lat, geoLng = geo_lng,
    deliveryZoneId = delivery_zone_id, deliveryFee = delivery_fee,
    isDefault = is_default, createdAt = created_at
)

fun CustomerAddress.toDbEntity() = Customer_addresses(
    id = id, customer_id = customerId, label = label,
    address = address, geo_lat = geoLat, geo_lng = geoLng,
    delivery_zone_id = deliveryZoneId, delivery_fee = deliveryFee,
    is_default = isDefault, created_at = createdAt
)

// ─── Recipe Mappers ──────────────────────────────────────────────
fun Recipes.toDomain(ingredients: List<RecipeIngredient> = emptyList()) = Recipe(
    id = id, vendorId = vendor_id, itemId = item_id,
    itemName = item_name, name = name, description = description,
    yieldQuantity = yield_quantity, yieldUnit = yield_unit,
    status = status, totalCost = total_cost,
    ingredients = ingredients,
    createdAt = created_at, updatedAt = updated_at
)

fun Recipe_ingredients.toDomain() = RecipeIngredient(
    stockId = stock_id, stockItemName = stock_item_name,
    quantity = quantity, unit = unit,
    fixedQuantity = fixed_quantity,
    displayOrder = display_order, availableQuantity = available_quantity
)
