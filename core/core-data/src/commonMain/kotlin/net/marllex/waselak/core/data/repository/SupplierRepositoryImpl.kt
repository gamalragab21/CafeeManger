package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.SupplierRepository
import net.marllex.waselak.core.model.PurchaseOrder
import net.marllex.waselak.core.model.Supplier
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreatePurchaseOrderRequest
import net.marllex.waselak.core.network.dto.CreateSupplierRequest
import net.marllex.waselak.core.network.dto.ReceivePurchaseOrderRequest
import net.marllex.waselak.core.network.dto.UpdateSupplierRequest
import net.marllex.waselak.core.network.mapper.toDomain

class SupplierRepositoryImpl(
    private val api: WaselakApiClient,
) : SupplierRepository {

    // ─── Suppliers ──────────────────────────────────────────────

    override suspend fun getSuppliers(active: Boolean?): Result<List<Supplier>> = runCatching {
        api.getSuppliers(active).map { it.toDomain() }
    }

    override suspend fun getSupplier(id: String): Result<Supplier> = runCatching {
        api.getSupplier(id).toDomain()
    }

    override suspend fun createSupplier(request: CreateSupplierRequest): Result<Supplier> = runCatching {
        api.createSupplier(request).toDomain()
    }

    override suspend fun updateSupplier(id: String, request: UpdateSupplierRequest): Result<Supplier> = runCatching {
        api.updateSupplier(id, request).toDomain()
    }

    override suspend fun deleteSupplier(id: String): Result<Unit> = runCatching {
        api.deleteSupplier(id)
    }

    // ─── Purchase Orders ────────────────────────────────────────

    override suspend fun getPurchaseOrders(status: String?, supplierId: String?, limit: Int, offset: Int): Result<List<PurchaseOrder>> = runCatching {
        api.getPurchaseOrders(status, supplierId, limit, offset).map { it.toDomain() }
    }

    override suspend fun getPurchaseOrder(id: String): Result<PurchaseOrder> = runCatching {
        api.getPurchaseOrder(id).toDomain()
    }

    override suspend fun createPurchaseOrder(request: CreatePurchaseOrderRequest): Result<PurchaseOrder> = runCatching {
        api.createPurchaseOrder(request).toDomain()
    }

    override suspend fun submitPurchaseOrder(id: String): Result<PurchaseOrder> = runCatching {
        api.submitPurchaseOrder(id).toDomain()
    }

    override suspend fun receivePurchaseOrder(id: String, request: ReceivePurchaseOrderRequest): Result<PurchaseOrder> = runCatching {
        api.receivePurchaseOrder(id, request).toDomain()
    }

    override suspend fun deletePurchaseOrder(id: String): Result<Unit> = runCatching {
        api.deletePurchaseOrder(id)
    }
}
