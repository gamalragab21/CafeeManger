package net.marllex.waselak.feature.manager.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.ReturnRepository
import net.marllex.waselak.core.domain.repository.TableRepository
import net.marllex.waselak.core.domain.repository.UserManagementRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderItem
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentStatus
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.network.dto.CreateOrderItemRequest
import net.marllex.waselak.core.network.dto.CreateReturnRequest
import net.marllex.waselak.core.network.dto.CreateReturnItemRequest
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.network.userFriendlyMessage

data class ReturnItemSelection(
    val orderItemId: String,
    val itemName: String,
    val maxQuantity: Int,
    val selectedQuantity: Int = 0,
    val reason: String = "",
)

class OrdersViewModel constructor(
    private val orderRepository: OrderRepository,
    private val userRepository: UserManagementRepository,
    private val tableRepository: TableRepository,
    private val returnRepository: ReturnRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    data class UiState(
        val orders: List<Order> = emptyList(),
        val selectedStatus: String? = null,
        val selectedChannel: String? = null,
        val selectedCashierId: String? = null,
        val selectedDeliveryUserId: String? = null,
        val selectedTableId: String? = null,
        val fromDate: Long? = null,
        val toDate: Long? = null,
        val cashiers: List<net.marllex.waselak.core.model.User> = emptyList(),
        val deliveryUsers: List<net.marllex.waselak.core.model.User> = emptyList(),
        val tables: List<net.marllex.waselak.core.model.Table> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        // Delivery assignment dialog
        val showAssignDeliveryDialog: Boolean = false,
        val assignOrderId: String? = null,
        /**
         * True when the assign-delivery dialog was opened by the
         * "Mark Completed" quick-action on a DELIVERY order that had no
         * driver assigned yet. After the user picks a driver and the
         * assignment succeeds, we automatically fire updateOrderStatus
         * (orderId, COMPLETED) so the cashier ends up exactly where they
         * tapped to go — without forcing them through the prep/ready/
         * assigned/out-for-delivery/delivered cascade.
         */
        val pendingCompleteAfterAssign: Boolean = false,
        /**
         * True while we're pulling a fresh delivery-user list from the API right before
         * showing the assign dialog. Ensures users added from another device (e.g. the
         * manager app on a different phone) show up without needing to leave+re-open
         * the orders screen.
         */
        val isRefreshingDeliveryUsers: Boolean = false,
        /** Non-null if the most recent refresh failed — surfaced as a "retry" link. */
        val refreshDeliveryUsersError: String? = null,
        // Edit order dialog
        val showEditOrderDialog: Boolean = false,
        val editingOrder: Order? = null,
        val editItems: List<OrderItem> = emptyList(),
        val editClientName: String = "",
        val editClientPhone: String = "",
        val editClientAddress: String = "",
        val editNotes: String = "",
        val isEditSaving: Boolean = false,
        // Payment dialog
        val showPaymentDialog: Boolean = false,
        val payingOrder: Order? = null,
        val selectedPaymentMethod: PaymentMethod = PaymentMethod.CASH,
        val isPaymentProcessing: Boolean = false,
        // Refund dialog
        val showRefundDialog: Boolean = false,
        val refundingOrder: Order? = null,
        val refundReason: String = "",
        val isRefundProcessing: Boolean = false,
        // Return dialog
        val showReturnDialog: Boolean = false,
        val returningOrder: Order? = null,
        val returnItems: List<ReturnItemSelection> = emptyList(),
        val returnReason: String = "",
        val returnType: String = "RETURN", // RETURN or EXCHANGE
        val isReturnProcessing: Boolean = false,
        // Exchange: replacement item selection
        val exchangeMenuItems: List<net.marllex.waselak.core.model.Item> = emptyList(),
        val exchangeSelectedItem: net.marllex.waselak.core.model.Item? = null,
        val exchangeSearchQuery: String = "",
        // Pagination
        val currentOffset: Int = 0,
        val totalCount: Int = 0,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        // Set of order IDs that have an in-flight status update so we can
        // both (1) debounce double-taps on the "Mark Completed" button and
        // (2) optionally surface a per-card spinner in the future. Used
        // by updateOrderStatus to ignore re-entrant calls.
        val statusUpdatesInFlight: Set<String> = emptySet(),
    ) {
        val hasActiveFilters: Boolean
            get() = selectedStatus != null || selectedChannel != null ||
                    selectedCashierId != null || selectedDeliveryUserId != null ||
                    selectedTableId != null ||
                    fromDate != null || toDate != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadOrders()
        loadUsers()
        loadTables()
    }

    private fun loadTables() {
        viewModelScope.launch {
            tableRepository.refreshTables()
        }
        viewModelScope.launch {
            tableRepository.getTables().collect { tables ->
                _uiState.update { it.copy(tables = tables) }
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            // Refresh from API first to populate local DB
            userRepository.refreshUsers()
        }
        viewModelScope.launch {
            userRepository.getUsers(UserRole.CASHIER).collect { cashiers ->
                _uiState.update { it.copy(cashiers = cashiers) }
            }
        }
        viewModelScope.launch {
            userRepository.getUsers(UserRole.DELIVERY).collect { deliveryUsers ->
                _uiState.update { it.copy(deliveryUsers = deliveryUsers) }
            }
        }
    }

    /**
     * Pulls the latest user list from the backend and saves to the local DB. The Flow
     * subscription in `loadUsers()` picks up the new rows automatically. Called
     * automatically when the assign-delivery dialog opens, and also exposed publicly
     * so the UI can surface a "retry" action if the initial refresh failed.
     *
     * Safe to call repeatedly — the UI debounces by checking `isRefreshingDeliveryUsers`
     * and short-circuits if a refresh is already in flight.
     */
    fun refreshDeliveryUsers() {
        if (_uiState.value.isRefreshingDeliveryUsers) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingDeliveryUsers = true, refreshDeliveryUsersError = null) }
            val result = userRepository.refreshUsers()
            _uiState.update {
                it.copy(
                    isRefreshingDeliveryUsers = false,
                    refreshDeliveryUsersError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /**
     * Load (or reload) the orders list.
     *
     * @param silent When true, skip the `isLoading=true` toggle so the
     *   screen doesn't flicker into a loading state. Use this for the
     *   background polling loop in OrdersScreen — that loop runs every
     *   10s and would otherwise flash a loading indicator on top of any
     *   ongoing optimistic update.
     */
    fun loadOrders(silent: Boolean = false) {
        viewModelScope.launch {
            val s = _uiState.value
            AppLogger.d("Orders", "Loading orders: silent=$silent status=${s.selectedStatus}, channel=${s.selectedChannel}")
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, error = null, currentOffset = 0) }
            }
            orderRepository.refreshOrders(
                status = s.selectedStatus,
                channel = s.selectedChannel,
                cashierId = s.selectedCashierId,
                deliveryUserId = s.selectedDeliveryUserId,
                tableId = s.selectedTableId,
                from = s.fromDate,
                to = s.toDate,
                limit = PAGE_SIZE,
                offset = 0,
            ).onSuccess { result ->
                _uiState.update { state ->
                    // ── Preserve optimistic in-flight rows ─────────────
                    // If an order has a status update in flight (cashier
                    // tapped "Mark Completed" seconds ago, request not
                    // yet acknowledged), keep the LOCAL optimistic copy.
                    // Without this, the polling refresh would clobber
                    // the optimistic state and the merchant sees the
                    // button reappear briefly until the original PATCH
                    // lands — the exact "flicker" complaint.
                    val inFlight = state.statusUpdatesInFlight
                    val merged = if (inFlight.isEmpty()) {
                        result.data
                    } else {
                        // Map by id for O(1) lookup of the optimistic
                        // version, then walk the server's list.
                        val optimisticById = state.orders
                            .filter { it.id in inFlight }
                            .associateBy { it.id }
                        result.data.map { serverOrder ->
                            optimisticById[serverOrder.id] ?: serverOrder
                        }
                    }
                    state.copy(
                        orders = merged,
                        isLoading = false,
                        currentOffset = merged.size,
                        totalCount = result.total,
                        hasMore = result.hasMore,
                    )
                }
            }.onFailure { e ->
                CrashReporter.captureException(e)
                AppLogger.e("Orders", "loadOrders failed: ${e::class.simpleName}: ${e.message}", e)
                // Silent polling failures shouldn't show error UI either.
                _uiState.update {
                    if (silent) it.copy(isLoading = false)
                    else it.copy(isLoading = false, error = e.userFriendlyMessage())
                }
            }
        }
    }

    fun loadMoreOrders() {
        val s = _uiState.value
        if (s.isLoadingMore || !s.hasMore) return
        viewModelScope.launch {
            AppLogger.d("Orders", "Loading more orders: offset=${s.currentOffset}")
            _uiState.update { it.copy(isLoadingMore = true) }
            orderRepository.refreshOrders(
                status = s.selectedStatus,
                channel = s.selectedChannel,
                cashierId = s.selectedCashierId,
                deliveryUserId = s.selectedDeliveryUserId,
                tableId = s.selectedTableId,
                from = s.fromDate,
                to = s.toDate,
                limit = PAGE_SIZE,
                offset = s.currentOffset,
            ).onSuccess { result ->
                _uiState.update {
                    it.copy(
                        orders = it.orders + result.data,
                        isLoadingMore = false,
                        currentOffset = it.currentOffset + result.data.size,
                        totalCount = result.total,
                        hasMore = result.hasMore,
                    )
                }
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }

    fun filterByStatus(status: String?) {
        AppLogger.d("Orders", "Filter by status: $status")
        _uiState.update { it.copy(selectedStatus = status) }
        loadOrders()
    }

    fun filterByChannel(channel: String?) {
        AppLogger.d("Orders", "Filter by channel: $channel")
        val currentStatus = _uiState.value.selectedStatus
        // Clear status filter if it's incompatible with new channel
        val resetStatus = if (currentStatus != null && channel != null) {
            val availableStatuses = when (channel) {
                "DINE_IN" -> OrderStatus.getAvailableStatuses(OrderChannel.DINE_IN)
                "DELIVERY" -> OrderStatus.getAvailableStatuses(OrderChannel.DELIVERY)
                "TAKEAWAY" -> OrderStatus.getAvailableStatuses(OrderChannel.TAKEAWAY)
                "IN_STORE" -> OrderStatus.getAvailableStatuses(OrderChannel.IN_STORE)
                "PICKUP_LATER" -> OrderStatus.getAvailableStatuses(OrderChannel.PICKUP_LATER)
                else -> OrderStatus.entries.toList()
            }
            if (availableStatuses.none { it.name == currentStatus }) null else currentStatus
        } else currentStatus
        _uiState.update { it.copy(selectedChannel = channel, selectedStatus = resetStatus) }
        loadOrders()
    }

    fun filterByCashier(cashierId: String?) {
        AppLogger.d("Orders", "Filter by cashier: $cashierId")
        _uiState.update { it.copy(selectedCashierId = cashierId) }
        loadOrders()
    }

    fun filterByDelivery(deliveryUserId: String?) {
        AppLogger.d("Orders", "Filter by delivery user: $deliveryUserId")
        _uiState.update { it.copy(selectedDeliveryUserId = deliveryUserId) }
        loadOrders()
    }

    fun filterByTable(tableId: String?) {
        AppLogger.d("Orders", "Filter by table: $tableId")
        _uiState.update { it.copy(selectedTableId = tableId) }
        loadOrders()
    }

    fun filterByDateRange(from: Long?, to: Long?) {
        AppLogger.d("Orders", "Filter by date range: from=$from, to=$to")
        _uiState.update { it.copy(fromDate = from, toDate = to) }
        loadOrders()
    }

    fun clearAllFilters() {
        AppLogger.d("Orders", "Clearing all filters")
        _uiState.update {
            it.copy(
                selectedStatus = null,
                selectedChannel = null,
                selectedCashierId = null,
                selectedDeliveryUserId = null,
                selectedTableId = null,
                fromDate = null,
                toDate = null
            )
        }
        loadOrders()
    }

    fun shareReceipt(orderId: String, onLink: (String) -> Unit) {
        AppLogger.d("Orders", "Sharing receipt: orderId=$orderId")
        viewModelScope.launch {
            orderRepository.shareReceipt(orderId)
                .onSuccess { link -> onLink(link.url) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        val currentOrder = _uiState.value.orders.find { it.id == orderId }
        AppLogger.d("Orders", "Updating order status: orderId=$orderId, from=${currentOrder?.status?.name} to=${newStatus.name}, channel=${currentOrder?.channel?.name}")
        // If transitioning to ASSIGNED on a delivery order, show the delivery person picker
        if (newStatus == OrderStatus.ASSIGNED) {
            if (currentOrder?.channel == net.marllex.waselak.core.model.OrderChannel.DELIVERY) {
                _uiState.update {
                    it.copy(
                        showAssignDeliveryDialog = true,
                        assignOrderId = orderId,
                        pendingCompleteAfterAssign = false,
                    )
                }
                refreshDeliveryUsers()
                return
            }
        }

        // ── DELIVERY-channel "Mark Completed" shortcut ────────────────
        //
        // Merchant request: when the cashier taps "Mark Completed" on a
        // DELIVERY order that has no driver assigned yet, don't silently
        // close it — pop the same driver-picker dialog used by the
        // ASSIGNED transition, then automatically complete the order
        // once a driver is picked. This way every completed delivery
        // ends up with an assigned driver in the audit trail.
        //
        // Skips the dialog when:
        //   • channel is not DELIVERY (other channels still close directly)
        //   • a driver is already assigned (no need to ask again — just
        //     fall through to the normal optimistic update below)
        if (newStatus == OrderStatus.COMPLETED &&
            currentOrder?.channel == net.marllex.waselak.core.model.OrderChannel.DELIVERY &&
            currentOrder.deliveryUserId.isNullOrBlank()
        ) {
            _uiState.update {
                it.copy(
                    showAssignDeliveryDialog = true,
                    assignOrderId = orderId,
                    pendingCompleteAfterAssign = true,
                )
            }
            refreshDeliveryUsers()
            return
        }

        // ── Optimistic UI update ──────────────────────────────────────
        //
        // Old flow waited for the server round-trip (1–3 s on hotspot)
        // before updating the local state, so the cashier saw NO visual
        // feedback for seconds. On failure it called `loadOrders()`
        // which full-screen-refreshed the list, causing the merchant's
        // complaint: button hidden for 3s, then reappears.
        //
        // New flow:
        //   1. Immediately swap the order's status in `state.orders` so
        //      the button disappears the instant the cashier taps.
        //   2. Mark the order as in-flight so a second tap is ignored
        //      (prevents duplicate API calls).
        //   3. Fire the network call in the background.
        //   4. On success, replace the optimistic copy with the server's
        //      response (which carries `updated_at`, etc.).
        //   5. On failure, ROLL BACK the local copy and surface the
        //      error — do NOT trigger a full reload. The list stays put;
        //      only the one row reverts.
        if (currentOrder == null) {
            AppLogger.w("Orders", "updateOrderStatus called for unknown orderId=$orderId — skipping")
            return
        }
        // Debounce double-taps.
        if (orderId in _uiState.value.statusUpdatesInFlight) {
            AppLogger.d("Orders", "Ignoring re-entrant status update for $orderId — already in flight")
            return
        }
        val previousStatus = currentOrder.status
        val optimisticOrder = currentOrder.copy(status = newStatus)

        _uiState.update { state ->
            state.copy(
                orders = state.orders.map { if (it.id == orderId) optimisticOrder else it },
                statusUpdatesInFlight = state.statusUpdatesInFlight + orderId,
            )
        }

        viewModelScope.launch {
            orderRepository.updateOrderStatus(orderId, newStatus)
                .onSuccess { updatedOrder ->
                    AppLogger.i("Orders", "Order status updated: orderId=$orderId, newStatus=${updatedOrder.status.name}")
                    _uiState.update { state ->
                        state.copy(
                            orders = state.orders.map { if (it.id == orderId) updatedOrder else it },
                            statusUpdatesInFlight = state.statusUpdatesInFlight - orderId,
                        )
                    }
                    // Refresh tables so DINE_IN table goes back to AVAILABLE after COMPLETED/CANCELLED
                    if (newStatus in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)) {
                        tableRepository.refreshTables()
                    }
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Orders", "Order status update failed: orderId=$orderId, target=${newStatus.name}", e)
                    // Roll the row back to the previous status — DON'T call
                    // loadOrders(). The merchant flagged the full-screen
                    // refresh as the source of the "button flickers back
                    // for 3 sec" behaviour.
                    _uiState.update { state ->
                        state.copy(
                            orders = state.orders.map {
                                if (it.id == orderId) it.copy(status = previousStatus) else it
                            },
                            statusUpdatesInFlight = state.statusUpdatesInFlight - orderId,
                            error = e.message,
                        )
                    }
                }
        }
    }

    fun dismissAssignDeliveryDialog() {
        _uiState.update {
            it.copy(
                showAssignDeliveryDialog = false,
                assignOrderId = null,
                pendingCompleteAfterAssign = false,
            )
        }
    }

    fun assignDeliveryUser(deliveryUserId: String) {
        val orderId = _uiState.value.assignOrderId ?: return
        // Capture this BEFORE clearing state so the chained complete still
        // fires even after the dialog state resets.
        val shouldCompleteAfter = _uiState.value.pendingCompleteAfterAssign
        viewModelScope.launch {
            AppLogger.d("Orders", "Assigning delivery user: $deliveryUserId to order $orderId (completeAfter=$shouldCompleteAfter)")
            // Don't close dialog immediately - wait for API response
            _uiState.update { it.copy(isLoading = true) }

            orderRepository.assignDeliveryUser(orderId, deliveryUserId)
                .onSuccess {
                    AppLogger.i("Orders", "Delivery user assigned successfully")
                    // Close dialog AND optimistically patch the order so
                    // its `deliveryUserId` is non-null before any chained
                    // updateOrderStatus reads from state. Without this
                    // patch the chained "Mark Completed" path below would
                    // re-enter the dialog branch (deliveryUserId would
                    // still be null in state until loadOrders() returns
                    // asynchronously — and we can't wait for it).
                    _uiState.update { state ->
                        state.copy(
                            showAssignDeliveryDialog = false,
                            assignOrderId = null,
                            pendingCompleteAfterAssign = false,
                            isLoading = false,
                            orders = state.orders.map {
                                if (it.id == orderId) it.copy(deliveryUserId = deliveryUserId) else it
                            },
                        )
                    }
                    // If the assign dialog was opened by the
                    // "Mark Completed" CTA, fire the actual complete now
                    // that a driver is on the order. This re-enters
                    // updateOrderStatus, which now skips the dialog
                    // branch (deliveryUserId is set) and runs the normal
                    // optimistic-update path. We deliberately do NOT
                    // call loadOrders() in this branch — the optimistic
                    // path will refresh the row from the server response.
                    if (shouldCompleteAfter) {
                        AppLogger.i("Orders", "Auto-completing order $orderId after delivery assignment")
                        updateOrderStatus(orderId, OrderStatus.COMPLETED)
                    } else {
                        loadOrders() // Refresh orders list (assign-only flow)
                    }
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Orders", "Failed to assign delivery user", e)
                    // Keep dialog open on error, show error message
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // ─── Edit Order ────────────────────────────────────────────────
    fun showEditOrder(order: Order) {
        AppLogger.i("Orders", "User action: open edit order dialog for orderId=${order.id}")
        _uiState.update {
            it.copy(
                showEditOrderDialog = true,
                editingOrder = order,
                editItems = order.items,
                editClientName = order.clientName ?: "",
                editClientPhone = order.clientPhone ?: "",
                editClientAddress = order.clientAddress ?: "",
                editNotes = order.notes ?: "",
            )
        }
    }

    fun dismissEditOrderDialog() {
        _uiState.update { it.copy(showEditOrderDialog = false, editingOrder = null) }
    }

    fun updateEditClientName(v: String) = _uiState.update { it.copy(editClientName = v) }
    fun updateEditClientPhone(v: String) = _uiState.update { it.copy(editClientPhone = v) }
    fun updateEditClientAddress(v: String) = _uiState.update { it.copy(editClientAddress = v) }
    fun updateEditNotes(v: String) = _uiState.update { it.copy(editNotes = v) }

    fun updateEditItemQuantity(itemId: String, quantity: Int) {
        AppLogger.d("Orders", "Updating edit item quantity: itemId=$itemId, quantity=$quantity")
        _uiState.update { state ->
            if (quantity <= 0) {
                state.copy(editItems = state.editItems.filter { it.id != itemId })
            } else {
                state.copy(editItems = state.editItems.map {
                    if (it.id == itemId) it.copy(quantity = quantity) else it
                })
            }
        }
    }

    fun removeEditItem(itemId: String) {
        AppLogger.d("Orders", "Removing edit item: itemId=$itemId")
        _uiState.update { state ->
            state.copy(editItems = state.editItems.filter { it.id != itemId })
        }
    }

    fun saveEditOrder() {
        val s = _uiState.value
        val order = s.editingOrder ?: return
        if (s.editItems.isEmpty()) return

        viewModelScope.launch {
            AppLogger.d("Orders", "Saving order edit: orderId=${order.id}, items=${s.editItems.size}")
            _uiState.update { it.copy(isEditSaving = true) }
            orderRepository.updateOrder(
                id = order.id,
                clientName = s.editClientName.ifBlank { null },
                clientPhone = s.editClientPhone.ifBlank { null },
                clientAddress = if (order.channel == OrderChannel.DELIVERY) s.editClientAddress.ifBlank { null } else null,
                notes = s.editNotes.ifBlank { null },
                items = s.editItems.map {
                    CreateOrderItemRequest(
                        itemId = it.itemId,
                        quantity = it.quantity,
                        note = it.note,
                    )
                },
            ).onSuccess {
                AppLogger.i("Orders", "Order edited successfully")
                _uiState.update { it.copy(isEditSaving = false, showEditOrderDialog = false, editingOrder = null) }
                loadOrders()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Orders", "Failed to edit order", e)
                _uiState.update { it.copy(isEditSaving = false, error = e.message) }
            }
        }
    }

    // ─── Payment Dialog ──────────────────────────────────────────────
    fun showPaymentDialog(order: Order) {
        AppLogger.i("Orders", "User action: open payment dialog for orderId=${order.id}")
        _uiState.update {
            it.copy(
                showPaymentDialog = true,
                payingOrder = order,
                selectedPaymentMethod = order.paymentMethod,
            )
        }
    }

    fun dismissPaymentDialog() {
        _uiState.update { it.copy(showPaymentDialog = false, payingOrder = null) }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        AppLogger.d("Orders", "Selected payment method: ${method.name}")
        _uiState.update { it.copy(selectedPaymentMethod = method) }
    }

    fun confirmPayment() {
        val s = _uiState.value
        val order = s.payingOrder ?: return
        viewModelScope.launch {
            AppLogger.d("Orders", "Confirming payment: orderId=${order.id}, method=${s.selectedPaymentMethod}")
            _uiState.update { it.copy(isPaymentProcessing = true) }
            orderRepository.updatePaymentStatus(
                id = order.id,
                status = PaymentStatus.PAID,
                paymentMethod = s.selectedPaymentMethod,
            ).onSuccess { updatedOrder ->
                AppLogger.i("Orders", "Payment confirmed")
                _uiState.update { state ->
                    state.copy(
                        isPaymentProcessing = false,
                        showPaymentDialog = false,
                        payingOrder = null,
                        orders = state.orders.map { if (it.id == order.id) updatedOrder else it },
                    )
                }
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Orders", "Payment failed", e)
                _uiState.update { it.copy(isPaymentProcessing = false, error = e.message) }
            }
        }
    }

    // ─── Refund ──────────────────────────────────────────────────────

    fun showRefundDialog(order: Order) {
        AppLogger.i("Orders", "User action: open refund dialog for orderId=${order.id}")
        _uiState.update { it.copy(showRefundDialog = true, refundingOrder = order, refundReason = "") }
    }

    fun dismissRefundDialog() {
        _uiState.update { it.copy(showRefundDialog = false, refundingOrder = null, refundReason = "") }
    }

    fun updateRefundReason(reason: String) {
        _uiState.update { it.copy(refundReason = reason) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun confirmRefund() {
        val order = _uiState.value.refundingOrder ?: return
        val reason = _uiState.value.refundReason
        if (reason.isBlank()) return

        viewModelScope.launch {
            AppLogger.d("Orders", "Confirming refund: orderId=${order.id}")
            _uiState.update { it.copy(isRefundProcessing = true) }
            orderRepository.refundOrder(order.id, reason).onSuccess {
                AppLogger.i("Orders", "Refund processed")
                _uiState.update {
                    it.copy(
                        isRefundProcessing = false,
                        showRefundDialog = false,
                        refundingOrder = null,
                        refundReason = "",
                    )
                }
                loadOrders()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Orders", "Refund failed", e)
                _uiState.update { it.copy(isRefundProcessing = false, error = e.message) }
            }
        }
    }

    // ─── Return / Exchange ─────────────────────────────────────────

    fun showReturnDialog(order: Order) {
        AppLogger.d("Orders", "showReturnDialog: orderId=${order.id}")
        viewModelScope.launch {
            orderRepository.fetchOrder(order.id).onSuccess { fullOrder ->
                // Get previous returns to calculate remaining returnable qty
                val previousReturns = returnRepository.getReturns(orderId = fullOrder.id).getOrNull() ?: emptyList()
                val returnedQtyMap = mutableMapOf<String, Int>()
                previousReturns.filter { it.status == "COMPLETED" }.forEach { ret ->
                    ret.items.forEach { ri ->
                        returnedQtyMap[ri.orderItemId] = (returnedQtyMap[ri.orderItemId] ?: 0) + ri.quantity
                    }
                }

                _uiState.update {
                    it.copy(
                        showReturnDialog = true,
                        returningOrder = fullOrder,
                        returnItems = fullOrder.items.mapNotNull { item ->
                            val alreadyReturned = returnedQtyMap[item.id] ?: 0
                            val remaining = item.quantity - alreadyReturned
                            if (remaining > 0) {
                                ReturnItemSelection(
                                    orderItemId = item.id,
                                    itemName = item.itemNameSnapshot,
                                    maxQuantity = remaining,
                                )
                            } else null // Hide fully returned items
                        },
                        returnReason = "",
                        returnType = "RETURN",
                    )
                }
            }
        }
    }

    fun dismissReturnDialog() {
        _uiState.update { it.copy(showReturnDialog = false, returningOrder = null, returnItems = emptyList()) }
    }

    fun setReturnType(type: String) {
        _uiState.update { it.copy(returnType = type) }
        if (type == "EXCHANGE" && _uiState.value.exchangeMenuItems.isEmpty()) {
            viewModelScope.launch {
                try {
                    val items = itemRepository.getItems().first()
                    _uiState.update { it.copy(exchangeMenuItems = items) }
                } catch (e: Exception) {
                    AppLogger.e("Orders", "Failed to load menu items for exchange", e)
                }
            }
        }
    }
    fun setReturnReason(reason: String) { _uiState.update { it.copy(returnReason = reason) } }
    fun setExchangeSearchQuery(query: String) { _uiState.update { it.copy(exchangeSearchQuery = query) } }
    fun selectExchangeItem(item: net.marllex.waselak.core.model.Item?) { _uiState.update { it.copy(exchangeSelectedItem = item) } }

    fun updateReturnItemQuantity(index: Int, qty: Int) {
        _uiState.update { state ->
            state.copy(returnItems = state.returnItems.toMutableList().apply {
                val item = this[index]
                this[index] = item.copy(selectedQuantity = qty.coerceIn(0, item.maxQuantity))
            })
        }
    }

    fun submitReturn() {
        val s = _uiState.value
        val order = s.returningOrder ?: return
        val selectedItems = s.returnItems.filter { it.selectedQuantity > 0 }
        if (selectedItems.isEmpty() || s.returnReason.isBlank()) return
        CrashReporter.logTransaction("submit_return", "refund")
        CrashReporter.logUserAction("submitReturn", "OrdersScreen", mapOf("orderId" to order.id, "type" to s.returnType, "items" to selectedItems.size.toString()))
        CrashReporter.captureMessage("Return submitted: ${s.returnType} for order ${order.id}")
        // For exchange, require a replacement item
        if (s.returnType == "EXCHANGE" && s.exchangeSelectedItem == null) return

        val reason = if (s.returnType == "EXCHANGE" && s.exchangeSelectedItem != null) {
            "${s.returnReason} | Replacement: ${s.exchangeSelectedItem.name}"
        } else s.returnReason

        AppLogger.d("Orders", "submitReturn: orderId=${order.id}, items=${selectedItems.size}, type=${s.returnType}")
        viewModelScope.launch {
            _uiState.update { it.copy(isReturnProcessing = true) }
            returnRepository.createReturn(
                CreateReturnRequest(
                    orderId = order.id,
                    returnType = s.returnType,
                    reason = reason,
                    items = selectedItems.map {
                        CreateReturnItemRequest(
                            orderItemId = it.orderItemId,
                            quantity = it.selectedQuantity,
                            reason = it.reason.ifBlank { null },
                        )
                    },
                    exchangeItemId = s.exchangeSelectedItem?.id,
                    exchangeQuantity = 1,
                )
            ).onSuccess {
                AppLogger.i("Orders", "Return created successfully")
                _uiState.update {
                    it.copy(
                        isReturnProcessing = false, showReturnDialog = false,
                        returningOrder = null, returnItems = emptyList(),
                        exchangeSelectedItem = null, exchangeSearchQuery = "",
                    )
                }
                loadOrders()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Orders", "Return failed", e)
                _uiState.update { it.copy(isReturnProcessing = false, error = e.message) }
            }
        }
    }
}
