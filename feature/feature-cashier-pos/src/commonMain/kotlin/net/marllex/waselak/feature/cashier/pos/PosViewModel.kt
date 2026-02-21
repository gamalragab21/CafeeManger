package net.marllex.waselak.feature.cashier.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.CategoryRepository
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.TableRepository
import net.marllex.waselak.core.domain.repository.TaxPlaceRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.CartItem
import net.marllex.waselak.core.model.Category
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.CustomerAddress
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentTiming
import net.marllex.waselak.core.model.Table
import net.marllex.waselak.core.model.TaxPlace
import net.marllex.waselak.core.network.dto.CreateOrderItemRequest

class PosViewModel constructor(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository,
    private val taxPlaceRepository: TaxPlaceRepository,
    private val vendorRepository: VendorRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    data class UiState(
        val items: List<Item> = emptyList(),
        val categories: List<Category> = emptyList(),
        val tables: List<Table> = emptyList(),
        val taxPlaces: List<TaxPlace> = emptyList(),
        val selectedCategoryId: String? = null,
        val cart: List<CartItem> = emptyList(),
        val channel: OrderChannel = OrderChannel.DINE_IN,
        val enableTables: Boolean = true,
        val enableDineIn: Boolean = true,
        val enableDelivery: Boolean = true,
        val enableTakeaway: Boolean = true,
        val enableInStore: Boolean = false,
        val enablePickupLater: Boolean = false,
        val vendorName: String = "",
        val vendorLogoUrl: String? = null,
        val selectedTableId: String? = null,
        val selectedTaxPlaceId: String? = null,
        val clientName: String = "",
        val clientPhone: String = "",
        val clientAddress: String = "",
        val notes: String = "",
        val isLoading: Boolean = true,
        val error: String? = null,
        val isSubmitting: Boolean = false,
        val createdOrder: Order? = null,
        // Customer integration fields
        val selectedCustomer: Customer? = null,
        val customerAddresses: List<CustomerAddress> = emptyList(),
        val selectedAddressId: String? = null,
        val recentOrders: List<Order> = emptyList(),
        val isLookingUpCustomer: Boolean = false,
        val customerLookupDone: Boolean = false,
        // Phone autocomplete dropdown
        val phoneSearchResults: List<Customer> = emptyList(),
        val showPhoneDropdown: Boolean = false,
    ) {
        /** Whether the Place Order button should be enabled */
        val canSubmit: Boolean get() {
            if (cart.isEmpty() || isSubmitting) return false
            return when (channel) {
                OrderChannel.DELIVERY -> clientPhone.isNotBlank() && clientAddress.isNotBlank()
                OrderChannel.TAKEAWAY -> clientPhone.isNotBlank()
                OrderChannel.DINE_IN -> true
                OrderChannel.IN_STORE -> true
                OrderChannel.PICKUP_LATER -> clientPhone.isNotBlank()
            }
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var phoneLookupJob: Job? = null

    init { loadMenu() }

    fun loadMenu() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Load vendor feature flags
            vendorRepository.refreshVendor().onSuccess { vendor ->
                val defaultChannel = when {
                    vendor.enableDineIn -> OrderChannel.DINE_IN
                    vendor.enableInStore -> OrderChannel.IN_STORE
                    vendor.enableDelivery -> OrderChannel.DELIVERY
                    vendor.enableTakeaway -> OrderChannel.TAKEAWAY
                    vendor.enablePickupLater -> OrderChannel.PICKUP_LATER
                    else -> OrderChannel.DINE_IN
                }
                _uiState.update { it.copy(
                    enableTables = vendor.enableTables,
                    enableDineIn = vendor.enableDineIn,
                    enableDelivery = vendor.enableDelivery,
                    enableTakeaway = vendor.enableTakeaway,
                    enableInStore = vendor.enableInStore,
                    enablePickupLater = vendor.enablePickupLater,
                    vendorName = vendor.name,
                    vendorLogoUrl = vendor.logoUrl,
                    channel = defaultChannel,
                ) }
            }
            itemRepository.refreshItems()
            categoryRepository.refreshCategories()
            tableRepository.refreshTables()
            // Preload tax places so the cashier can pick fees immediately
            taxPlaceRepository.getTaxPlaces().onSuccess { places ->
                _uiState.update { state ->
                    state.copy(
                        taxPlaces = places,
                        selectedTaxPlaceId = state.selectedTaxPlaceId
                            ?: places.firstOrNull { it.isDefault }?.id
                            ?: places.firstOrNull()?.id
                    )
                }
            }

            combine(
                itemRepository.getAvailableItems(),
                categoryRepository.getCategories(),
                tableRepository.getAvailableTables(),
            ) { items, categories, tables ->
                _uiState.value.copy(
                    items = items, categories = categories, tables = tables, isLoading = false,
                )
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun addToCart(item: Item) {
        _uiState.update { state ->
            val existing = state.cart.find { it.item.id == item.id }
            val newCart = if (existing != null) {
                state.cart.map {
                    if (it.item.id == item.id) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                state.cart + CartItem(item = item, quantity = 1)
            }
            state.copy(cart = newCart)
        }
    }

    fun removeFromCart(itemId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filter { it.item.id != itemId })
        }
    }

    fun updateCartQuantity(itemId: String, quantity: Int) {
        if (quantity <= 0) {
            removeFromCart(itemId)
            return
        }
        _uiState.update { state ->
            state.copy(cart = state.cart.map {
                if (it.item.id == itemId) it.copy(quantity = quantity) else it
            })
        }
    }

    fun setChannel(channel: OrderChannel) {
        _uiState.update {
            it.copy(
                channel = channel,
                selectedTaxPlaceId = if (channel == OrderChannel.DELIVERY) {
                    it.selectedTaxPlaceId ?: it.taxPlaces.firstOrNull { tp -> tp.isDefault }?.id
                } else null,
            )
        }
        // Clear customer data when switching to channels that don't need it
        val needsCustomer = channel in listOf(OrderChannel.DELIVERY, OrderChannel.TAKEAWAY, OrderChannel.PICKUP_LATER)
        if (!needsCustomer) {
            clearCustomer()
        }
        if (channel == OrderChannel.DELIVERY && _uiState.value.taxPlaces.isEmpty()) {
            viewModelScope.launch {
                taxPlaceRepository.getTaxPlaces().onSuccess { places ->
                    _uiState.update { state ->
                        state.copy(
                            taxPlaces = places,
                            selectedTaxPlaceId = state.selectedTaxPlaceId ?: places.firstOrNull { it.isDefault }?.id ?: places.firstOrNull()?.id
                        )
                    }
                }
            }
        }
    }

    fun setSelectedTaxPlaceId(taxPlaceId: String?) {
        _uiState.update { it.copy(selectedTaxPlaceId = taxPlaceId) }
    }

    fun setTableId(tableId: String?) { _uiState.update { it.copy(selectedTableId = tableId) } }
    fun setClientName(v: String) { _uiState.update { it.copy(clientName = v) } }

    fun setClientPhone(v: String) {
        _uiState.update { it.copy(clientPhone = v) }

        // Local search for autocomplete dropdown (3+ chars)
        if (v.length >= 3) {
            phoneLookupJob?.cancel()
            phoneLookupJob = viewModelScope.launch {
                delay(200)
                // Search local DB for matching customers
                customerRepository.searchCustomers(v)
                    .collect { results ->
                        _uiState.update {
                            it.copy(
                                phoneSearchResults = results,
                                showPhoneDropdown = results.isNotEmpty() && it.selectedCustomer == null,
                            )
                        }
                    }
            }
            // Also do remote lookup when phone has 11+ chars
            if (v.length >= 11) {
                viewModelScope.launch {
                    delay(300)
                    _uiState.update { it.copy(isLookingUpCustomer = true) }
                    customerRepository.lookupCustomerByPhone(v)
                        .onSuccess { customer ->
                            if (customer != null) {
                                _selectCustomer(customer)
                            } else {
                                _uiState.update {
                                    it.copy(isLookingUpCustomer = false, customerLookupDone = true)
                                }
                            }
                        }
                        .onFailure {
                            _uiState.update {
                                it.copy(isLookingUpCustomer = false, customerLookupDone = true)
                            }
                        }
                }
            }
        } else {
            phoneLookupJob?.cancel()
            _uiState.update {
                it.copy(
                    isLookingUpCustomer = false,
                    customerLookupDone = false,
                    selectedCustomer = null,
                    customerAddresses = emptyList(),
                    selectedAddressId = null,
                    recentOrders = emptyList(),
                    phoneSearchResults = emptyList(),
                    showPhoneDropdown = false,
                )
            }
        }
    }

    /** Called when user picks a customer from the phone dropdown */
    fun selectCustomerFromDropdown(customer: Customer) {
        _uiState.update {
            it.copy(
                clientPhone = customer.phone,
                showPhoneDropdown = false,
                phoneSearchResults = emptyList(),
            )
        }
        phoneLookupJob?.cancel()
        _selectCustomer(customer)
    }

    fun dismissPhoneDropdown() {
        _uiState.update { it.copy(showPhoneDropdown = false) }
    }

    /** Shared helper to select a customer and fill name/address/orders */
    private fun _selectCustomer(customer: Customer) {
        _uiState.update {
            it.copy(
                selectedCustomer = customer,
                isLookingUpCustomer = false,
                customerLookupDone = true,
                showPhoneDropdown = false,
                phoneSearchResults = emptyList(),
                clientName = customer.name.orEmpty().ifBlank { it.clientName },
            )
        }
        viewModelScope.launch {
            // Load saved addresses
            val addresses = customerRepository.getCustomerAddresses(customer.id)
                .getOrDefault(emptyList())

            // Load recent orders
            val orders = customerRepository.getCustomerRecentOrders(customer.id)
                .getOrDefault(emptyList())

            // Pick the best address to auto-fill:
            // 1. Saved default address, 2. Any saved address, 3. Last order's delivery address
            val defaultAddr = addresses.find { it.isDefault } ?: addresses.firstOrNull()
            val autoFillAddress = defaultAddr?.address
                ?: orders.firstOrNull()?.clientAddress
                ?: ""

            _uiState.update {
                it.copy(
                    customerAddresses = addresses,
                    selectedAddressId = defaultAddr?.id,
                    clientAddress = autoFillAddress,
                    recentOrders = orders,
                )
            }
        }
    }

    fun setClientAddress(v: String) { _uiState.update { it.copy(clientAddress = v) } }
    fun setNotes(v: String) { _uiState.update { it.copy(notes = v) } }

    fun getSubtotal(): Double = _uiState.value.cart.fold(0.0) { acc, cartItem -> acc + cartItem.item.price * cartItem.quantity }

    // ─── Customer methods ─────────────────────────────────────────

    fun selectCustomerAddress(addressId: String) {
        val address = _uiState.value.customerAddresses.find { it.id == addressId }
        _uiState.update {
            it.copy(
                selectedAddressId = addressId,
                clientAddress = address?.address.orEmpty(),
            )
        }
    }

    fun clearCustomer() {
        phoneLookupJob?.cancel()
        _uiState.update {
            it.copy(
                selectedCustomer = null,
                customerAddresses = emptyList(),
                selectedAddressId = null,
                recentOrders = emptyList(),
                customerLookupDone = false,
                phoneSearchResults = emptyList(),
                showPhoneDropdown = false,
            )
        }
    }

    fun reorderFromHistory(order: Order) {
        _uiState.update { state ->
            val currentItems = state.items
            val newCart = order.items.mapNotNull { orderItem ->
                currentItems.find { it.id == orderItem.itemId }?.let { item ->
                    CartItem(item = item, quantity = orderItem.quantity, note = orderItem.note)
                }
            }
            state.copy(cart = newCart)
        }
    }

    // ─── Submit & Clear ───────────────────────────────────────────

    fun submitOrder(paymentMethod: PaymentMethod, paymentTiming: PaymentTiming = PaymentTiming.PAY_NOW, onSuccess: (Order) -> Unit) {
        val s = _uiState.value
        if (!s.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            // Auto-create customer on submit if phone entered but no selectedCustomer
            var customerId = s.selectedCustomer?.id
            if (customerId == null && s.clientPhone.isNotBlank()) {
                customerRepository.createCustomer(
                    phone = s.clientPhone,
                    name = s.clientName.ifBlank { null },
                ).onSuccess { newCustomer ->
                    customerId = newCustomer.id
                    _uiState.update { it.copy(selectedCustomer = newCustomer) }
                }
            }

            val orderItems = s.cart.map { cartItem ->
                CreateOrderItemRequest(
                    itemId = cartItem.item.id,
                    quantity = cartItem.quantity,
                    note = cartItem.note,
                )
            }

            val clientAddress = when (s.channel) {
                OrderChannel.DELIVERY -> s.clientAddress.ifBlank { null }
                OrderChannel.TAKEAWAY -> s.clientAddress.ifBlank { null }
                OrderChannel.PICKUP_LATER -> s.clientAddress.ifBlank { null }
                else -> null
            }

            orderRepository.createOrder(
                channel = s.channel,
                tableId = if (s.channel == OrderChannel.DINE_IN) s.selectedTableId else null,
                clientName = s.clientName.ifBlank { null },
                clientPhone = s.clientPhone.ifBlank { null },
                clientAddress = clientAddress,
                customerId = customerId,
                geoLat = null, geoLng = null,
                paymentMethod = paymentMethod,
                paymentTiming = paymentTiming,
                taxPlaceId = if (s.channel == OrderChannel.DELIVERY) s.selectedTaxPlaceId else null,
                notes = s.notes.ifBlank { null },
                items = orderItems,
            ).onSuccess { order ->
                // Call onSuccess first (dismiss sheet) while cart is still non-empty,
                // so the BottomSheet is still in the composition tree
                onSuccess(order)
                _uiState.update { it.copy(isSubmitting = false, createdOrder = order, cart = emptyList()) }
                // Table is automatically set to OCCUPIED by backend for dine-in orders
                // Refresh tables to reflect the new status locally
                if (s.channel == OrderChannel.DINE_IN && s.selectedTableId != null) {
                    tableRepository.refreshTables()
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }

    fun clearOrder() {
        phoneLookupJob?.cancel()
        _uiState.update {
            it.copy(
                cart = emptyList(), createdOrder = null,
                clientName = "", clientPhone = "", clientAddress = "",
                notes = "", selectedTableId = null,
                selectedTaxPlaceId = _uiState.value.taxPlaces.firstOrNull { it.isDefault }?.id,
                // Clear customer-related state
                selectedCustomer = null,
                customerAddresses = emptyList(),
                selectedAddressId = null,
                recentOrders = emptyList(),
                customerLookupDone = false,
                isLookingUpCustomer = false,
                phoneSearchResults = emptyList(),
                showPhoneDropdown = false,
            )
        }
    }
}
