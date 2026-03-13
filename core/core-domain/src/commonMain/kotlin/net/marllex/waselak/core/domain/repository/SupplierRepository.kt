package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.PurchaseOrder
import net.marllex.waselak.core.model.Supplier
import net.marllex.waselak.core.network.dto.CreatePurchaseOrderRequest
import net.marllex.waselak.core.network.dto.CreateSupplierRequest
import net.marllex.waselak.core.network.dto.ReceivePurchaseOrderRequest
import net.marllex.waselak.core.network.dto.UpdateSupplierRequest

interface SupplierRepository {
    // Suppliers
    suspend fun getSuppliers(active: Boolean? = null): Result<List<Supplier>>
    suspend fun getSupplier(id: String): Result<Supplier>
    suspend fun createSupplier(request: CreateSupplierRequest): Result<Supplier>
    suspend fun updateSupplier(id: String, request: UpdateSupplierRequest): Result<Supplier>
    suspend fun deleteSupplier(id: String): Result<Unit>

    // Purchase Orders
    suspend fun getPurchaseOrders(status: String? = null, supplierId: String? = null, limit: Int = 50, offset: Int = 0): Result<List<PurchaseOrder>>
    suspend fun getPurchaseOrder(id: String): Result<PurchaseOrder>
    suspend fun createPurchaseOrder(request: CreatePurchaseOrderRequest): Result<PurchaseOrder>
    suspend fun submitPurchaseOrder(id: String): Result<PurchaseOrder>
    suspend fun receivePurchaseOrder(id: String, request: ReceivePurchaseOrderRequest): Result<PurchaseOrder>
    suspend fun deletePurchaseOrder(id: String): Result<Unit>
}
