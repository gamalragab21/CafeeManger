package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Vendors

class VendorDao(private val db: WaselakDatabase) {
    private val queries get() = db.vendorQueries

    fun getVendorById(id: String): Flow<Vendors?> =
        queries.getVendorById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun insertVendor(vendor: Vendors) {
        queries.insertVendor(
            id = vendor.id,
            name = vendor.name,
            logo_url = vendor.logo_url,
            address = vendor.address,
            contact_phone = vendor.contact_phone,
            wallet_phone = vendor.wallet_phone,
            default_delivery_fee = vendor.default_delivery_fee,
            store_type = vendor.store_type,
            enable_tables = vendor.enable_tables,
            enable_dine_in = vendor.enable_dine_in,
            enable_delivery = vendor.enable_delivery,
            enable_takeaway = vendor.enable_takeaway,
            enable_in_store = vendor.enable_in_store,
            enable_pickup_later = vendor.enable_pickup_later,
            business_type = vendor.business_type,
            tax_enabled = vendor.tax_enabled,
            default_tax_percent = vendor.default_tax_percent,
            stock_mode = vendor.stock_mode,
            digital_menu_url = vendor.digital_menu_url,
            created_at = vendor.created_at,
            updated_at = vendor.updated_at
        )
    }

    suspend fun updateVendor(vendor: Vendors) {
        queries.updateVendor(
            name = vendor.name,
            logo_url = vendor.logo_url,
            address = vendor.address,
            contact_phone = vendor.contact_phone,
            wallet_phone = vendor.wallet_phone,
            default_delivery_fee = vendor.default_delivery_fee,
            store_type = vendor.store_type,
            enable_tables = vendor.enable_tables,
            enable_dine_in = vendor.enable_dine_in,
            enable_delivery = vendor.enable_delivery,
            enable_takeaway = vendor.enable_takeaway,
            enable_in_store = vendor.enable_in_store,
            enable_pickup_later = vendor.enable_pickup_later,
            business_type = vendor.business_type,
            tax_enabled = vendor.tax_enabled,
            default_tax_percent = vendor.default_tax_percent,
            stock_mode = vendor.stock_mode,
            digital_menu_url = vendor.digital_menu_url,
            updated_at = vendor.updated_at,
            id = vendor.id
        )
    }

    suspend fun deleteVendor(id: String) {
        queries.deleteVendor(id)
    }
}
