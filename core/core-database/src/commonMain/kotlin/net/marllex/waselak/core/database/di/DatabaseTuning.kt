package net.marllex.waselak.core.database.di

import app.cash.sqldelight.db.SqlDriver

/**
 * Runtime-added indexes that ensure existing installs get the same indexes as new installs
 * (Schema.create() is only called for brand-new DBs).
 *
 * All statements use IF NOT EXISTS, so re-running them on every startup is cheap.
 * Kept as DDL statements (no result rows) so they execute cleanly on every driver.
 */
fun SqlDriver.applyPerformanceIndexes() {
    for (sql in PERFORMANCE_INDEXES) {
        runCatching { execute(null, sql, 0) }
    }
}

private val PERFORMANCE_INDEXES = listOf(
    // items — POS catalog filtering
    "CREATE INDEX IF NOT EXISTS idx_items_vendor_category ON items(vendor_id, category_id)",
    "CREATE INDEX IF NOT EXISTS idx_items_vendor_available ON items(vendor_id, available)",
    "CREATE INDEX IF NOT EXISTS idx_items_barcode ON items(vendor_id, barcode)",
    "CREATE INDEX IF NOT EXISTS idx_items_sku ON items(vendor_id, sku)",
    // orders — list filtering and sync
    "CREATE INDEX IF NOT EXISTS idx_orders_vendor_created ON orders(vendor_id, created_at DESC)",
    "CREATE INDEX IF NOT EXISTS idx_orders_vendor_status ON orders(vendor_id, status)",
    "CREATE INDEX IF NOT EXISTS idx_orders_vendor_channel ON orders(vendor_id, channel)",
    "CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id, created_at DESC)",
    "CREATE INDEX IF NOT EXISTS idx_orders_delivery_user ON orders(delivery_user_id, status)",
    "CREATE INDEX IF NOT EXISTS idx_orders_sync_status ON orders(sync_status, created_at)",
    // order_items — fetched per order during list rendering
    "CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id)",
    // pending_sync — scanned ordered on every sync
    "CREATE INDEX IF NOT EXISTS idx_pending_sync_type_created ON pending_sync(type, created_at)",
    "CREATE INDEX IF NOT EXISTS idx_pending_sync_created ON pending_sync(created_at)",
    // item_variants — batch-loaded after item list fetch
    "CREATE INDEX IF NOT EXISTS idx_variant_groups_item ON item_variant_groups(item_id, display_order)",
    "CREATE INDEX IF NOT EXISTS idx_variant_options_group ON item_variant_options(group_id, display_order)",
)
