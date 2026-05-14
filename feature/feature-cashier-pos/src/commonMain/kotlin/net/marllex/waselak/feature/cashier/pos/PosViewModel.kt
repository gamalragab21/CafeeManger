package net.marllex.waselak.feature.cashier.pos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.CategoryRepository
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.OfferRepository
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.ScheduledOrderRepository
import net.marllex.waselak.core.domain.repository.TableRepository
import net.marllex.waselak.core.domain.repository.TaxPlaceRepository
import net.marllex.waselak.core.domain.repository.DrugInteractionRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.InteractionCheckResult
import net.marllex.waselak.core.model.CartItem
import net.marllex.waselak.core.model.Category
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.CustomerAddress
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Offer
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentTiming
import net.marllex.waselak.core.model.Table
import net.marllex.waselak.core.model.VariantSelection
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.model.TaxPlace
import net.marllex.waselak.core.network.dto.CreateOrderItemRequest
import net.marllex.waselak.core.network.dto.CreateScheduledOrderItemRequest
import net.marllex.waselak.core.network.dto.CreateScheduledOrderRequest
import net.marllex.waselak.core.network.dto.VariantSelectionRequest
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.network.isPlanLimitExceeded
import net.marllex.waselak.core.network.userFriendlyMessage
import net.marllex.waselak.core.common.crash.CrashReporter

class PosViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository,
    private val scheduledOrderRepository: ScheduledOrderRepository,
    private val taxPlaceRepository: TaxPlaceRepository,
    private val vendorRepository: VendorRepository,
    private val customerRepository: CustomerRepository,
    private val offerRepository: OfferRepository,
    private val offlineModeManager: OfflineModeManager,
    private val api: WaselakApiClient,
    private val drugInteractionRepository: DrugInteractionRepository,
) : ViewModel() {

    // Navigation args from reservation flow
    private val navTableId: String? = savedStateHandle["tableId"]
    private val navReservationId: String? = savedStateHandle["reservationId"]
    private val navClientName: String? = savedStateHandle["clientName"]
    private val navClientPhone: String? = savedStateHandle["clientPhone"]

    @Immutable
    data class UiState(
        val items: List<Item> = emptyList(),
        val categories: List<Category> = emptyList(),
        val tables: List<Table> = emptyList(),
        val taxPlaces: List<TaxPlace> = emptyList(),
        val selectedCategoryId: String? = null,
        /** What's in the TextField right now — updates on every keystroke. */
        val searchInput: String = "",
        /** Committed query used to filter items — 200ms debounced. */
        val searchQuery: String = "",
        val cart: List<CartItem> = emptyList(),
        val channel: OrderChannel = OrderChannel.DINE_IN,
        val enableTables: Boolean = true,
        val enableDineIn: Boolean = true,
        val enableDelivery: Boolean = true,
        val enableTakeaway: Boolean = true,
        val enableInStore: Boolean = false,
        val enablePickupLater: Boolean = false,
        val enableCustomerCredit: Boolean = false,
        val vendorName: String = "",
        val vendorLogoUrl: String? = null,
        val businessType: String = "RESTAURANT",
        // Capability flags derived once from `DomainFeatures.forVendor(vendor)` plus the
        // vendor's `enable*` toggles. Replacing the previous `businessType == "PHARMACY"`
        // string compares — those silently broke whenever the value's casing varied
        // (`"pharmacy"`, `"Pharmacy"`) or when an admin had set the wrong type.
        // `canUseX = (DomainFeatures says this business *type* supports X)
        //         && (vendor has explicitly enabled X — defaults to true if no toggle)`.
        val canUsePrescriptions: Boolean = false,
        val canUseDrugInteractions: Boolean = false,
        val canUseBarcode: Boolean = false,
        val canUseCustomerCredit: Boolean = false,
        val canUseSplitPayments: Boolean = true,
        val selectedTableId: String? = null,
        val selectedDeliveryUserId: String? = null,
        val reservationId: String? = null,
        val selectedTaxPlaceId: String? = null,
        // Vendor's fallback delivery fee (used when no zone is picked).
        // Mirrors the backend behaviour in OrderRoutes — zone > vendor
        // default. Surfacing it in UiState lets the POS confirmation
        // show the same number the receipt will print.
        val defaultDeliveryFee: Double = 0.0,
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
        // Offline mode
        val isOffline: Boolean = false,
        // Variant selector
        val variantSelectorItem: Item? = null,
        val variantSelections: Map<String, VariantSelection> = emptyMap(), // groupId → selection
        // Barcode scanner
        val showBarcodeScanner: Boolean = false,
        val barcodeScanMessage: String? = null,
        // Offers
        val activeOffers: List<Offer> = emptyList(),
        val appliedOffer: Offer? = null,
        // Manual discount
        val manualDiscountValue: String = "",
        val manualDiscountType: String = "FIXED", // FIXED or PERCENT
        val manualDiscountReason: String = "",
        // Loyalty points
        val pointsToRedeem: Int = 0,
        val loyaltyEnabled: Boolean = false,
        val pointsRedeemRate: Double = 0.1,
        val minPointsRedeem: Int = 100,
        // PIN approval
        val showPinDialog: Boolean = false,
        val pinError: String? = null,
        val maxManualDiscountPercent: Double = 100.0,
        val manualDiscountRequiresPin: Boolean = false,
        val pinApproved: Boolean = false,
        /**
         * Short-lived manager override token returned by `/api/v1/auth/verify-override-pin`.
         * Attached to the next `createOrder()` call when the discount > 0 and the
         * cashier is not a manager. Cleared after a successful submission so a stale
         * token can't be replayed for a later order.
         */
        val managerOverrideToken: String? = null,
        val managerApprovedByName: String? = null,
        // Offline confirmation dialog
        val showOfflineConfirmation: Boolean = false,
        // Scheduled order (PICKUP_LATER)
        val scheduledFor: Long? = null,
        // Doctor / diagnosis (pharmacy)
        val doctorName: String = "",
        val diagnosis: String = "",
        val showDrugInteractionWarning: Boolean = false,
        val drugInteractionResult: InteractionCheckResult? = null,
        // Plan limit / feature gating
        val showPlanLimitDialog: Boolean = false,
        val planLimitMessage: String = "",
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",
    ) {
        /** Items filtered by search query (cross-category) or by selected category */
        val filteredItems: List<Item>
            get() = when {
                searchQuery.isNotBlank() -> items.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }
                selectedCategoryId != null -> items.filter {
                    it.categoryId == selectedCategoryId
                }
                else -> items
            }

        /** Whether the Place Order button should be enabled */
        val canSubmit: Boolean get() {
            if (cart.isEmpty() || isSubmitting) return false
            return when (channel) {
                // Delivery still requires phone + address — without them
                // we have no way to dispatch the order.
                OrderChannel.DELIVERY -> clientPhone.isNotBlank() && clientAddress.isNotBlank()
                // TAKEAWAY now has NO required client fields. The merchant
                // flagged this because most takeaway customers walk up to
                // the counter with no need to leave a phone — forcing one
                // slowed every walk-up sale. Phone/name/address are all
                // optional and free to leave blank.
                OrderChannel.TAKEAWAY -> true
                OrderChannel.DINE_IN -> true
                OrderChannel.IN_STORE -> true
                // Pickup-later still needs a phone — the cashier calls the
                // customer when the order is ready.
                OrderChannel.PICKUP_LATER -> clientPhone.isNotBlank() && scheduledFor != null
            }
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var phoneLookupJob: Job? = null
    private var searchDebounceJob: Job? = null

    init {
        loadMenu()
        viewModelScope.launch {
            offlineModeManager.isOfflineActive.collect { offline ->
                _uiState.update { it.copy(isOffline = offline) }
            }
        }
        viewModelScope.launch {
            offlineModeManager.needsOfflineConfirmation.collect { needsConfirm ->
                _uiState.update { it.copy(showOfflineConfirmation = needsConfirm) }
            }
        }
        // Auto-prefill from reservation navigation args
        if (navReservationId != null && navTableId != null && navClientName != null) {
            prefillFromReservation(navTableId, navReservationId, navClientName, navClientPhone)
        }
    }

    fun loadMenu() {
        AppLogger.d("POS", "Loading POS menu")
        // Subscribe to local DB flows immediately (shows cached data even when offline)
        viewModelScope.launch {
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
        // Load vendor feature flags from local DB
        viewModelScope.launch {
            vendorRepository.getMyVendor().collect { vendor ->
                if (vendor != null) {
                    val defaultChannel = when {
                        vendor.enableDineIn -> OrderChannel.DINE_IN
                        vendor.enableInStore -> OrderChannel.IN_STORE
                        vendor.enableDelivery -> OrderChannel.DELIVERY
                        vendor.enableTakeaway -> OrderChannel.TAKEAWAY
                        vendor.enablePickupLater -> OrderChannel.PICKUP_LATER
                        else -> OrderChannel.DINE_IN
                    }
                    // Compute the *capability* flags once from DomainFeatures + vendor toggles
                    // so checks elsewhere don't have to compare raw business-type strings.
                    val features = net.marllex.waselak.core.model.DomainFeatures.forVendor(vendor)
                    _uiState.update { it.copy(
                        enableTables = vendor.enableTables,
                        enableDineIn = vendor.enableDineIn,
                        enableDelivery = vendor.enableDelivery,
                        enableTakeaway = vendor.enableTakeaway,
                        enableInStore = vendor.enableInStore,
                        enablePickupLater = vendor.enablePickupLater,
                        enableCustomerCredit = vendor.enableCustomerCredit,
                        vendorName = vendor.name,
                        vendorLogoUrl = vendor.logoUrl,
                        businessType = vendor.businessType,
                        channel = if (_uiState.value.cart.isEmpty()) defaultChannel else _uiState.value.channel,
                        loyaltyEnabled = vendor.loyaltyEnabled,
                        pointsRedeemRate = vendor.pointsRedeemRate,
                        minPointsRedeem = vendor.minPointsRedeem,
                        defaultDeliveryFee = vendor.defaultDeliveryFee,
                        maxManualDiscountPercent = vendor.maxManualDiscountPercent,
                        manualDiscountRequiresPin = vendor.manualDiscountRequiresPin,
                        canUsePrescriptions = features.hasPrescriptions && vendor.enablePrescriptions,
                        canUseDrugInteractions = features.hasDrugInteractions && vendor.enableDrugInteractions,
                        canUseBarcode = features.hasBarcode,
                        canUseCustomerCredit = features.hasCustomerCredit && vendor.enableCustomerCredit,
                        canUseSplitPayments = features.hasSplitPayments,
                    ) }
                }
            }
        }
        // Load active offers from local DB
        viewModelScope.launch {
            offerRepository.getActiveOffers()
                .catch { e -> AppLogger.e("POS", "Error loading active offers", e) }
                .collect { offers ->
                    _uiState.update { it.copy(activeOffers = offers) }
                }
        }
        // Refresh from API in PARALLEL (non-blocking — failures are silent when offline)
        viewModelScope.launch { vendorRepository.refreshVendor() }
        viewModelScope.launch { itemRepository.refreshItems() }
        viewModelScope.launch { categoryRepository.refreshCategories() }
        viewModelScope.launch { tableRepository.refreshTables() }
        viewModelScope.launch { customerRepository.refreshCustomers() }
        viewModelScope.launch { offerRepository.refreshOffers() }
        viewModelScope.launch {
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
        }
    }

    fun selectCategory(categoryId: String?) {
        AppLogger.d("POS", "Select category: $categoryId")
        searchDebounceJob?.cancel()
        _uiState.update { it.copy(selectedCategoryId = categoryId, searchInput = "", searchQuery = "") }
    }

    fun updateSearchQuery(query: String) {
        // Update the raw input immediately so the TextField stays responsive.
        _uiState.update { it.copy(searchInput = query) }
        // Debounce the committed searchQuery (what drives filteredItems) by 200ms — skips
        // re-filtering + recomposing the grid on every keystroke while the user is typing.
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            // Blank resets instantly so "clear search" feels snappy.
            _uiState.update { it.copy(searchQuery = "") }
        } else {
            searchDebounceJob = viewModelScope.launch {
                delay(200)
                _uiState.update { it.copy(searchQuery = query) }
            }
        }
    }

    fun addToCart(item: Item) {
        AppLogger.d("POS", "Adding to cart: item=${item.name}, hasVariants=${item.variantGroups.isNotEmpty()}")
        // If item has variant groups, show variant selector
        if (item.variantGroups.isNotEmpty()) {
            // Pre-select defaults
            val defaultSelections = mutableMapOf<String, VariantSelection>()
            item.variantGroups.forEach { group ->
                val defaultOption = group.options.find { it.isDefault }
                if (defaultOption != null) {
                    defaultSelections[group.id] = VariantSelection(
                        groupName = group.name,
                        optionName = defaultOption.name,
                        priceAdjustment = defaultOption.priceAdjustment,
                    )
                }
            }
            _uiState.update { it.copy(variantSelectorItem = item, variantSelections = defaultSelections) }
            return
        }
        // No variants — add directly
        addToCartDirect(item, emptyList())
    }

    fun selectVariantOption(groupId: String, groupName: String, optionName: String, priceAdjustment: Double) {
        _uiState.update { state ->
            state.copy(variantSelections = state.variantSelections + (groupId to VariantSelection(
                groupName = groupName, optionName = optionName, priceAdjustment = priceAdjustment
            )))
        }
    }

    fun confirmVariantSelection() {
        val item = _uiState.value.variantSelectorItem ?: return
        AppLogger.d("POS", "Confirming variant selection for item=${item.name}")
        val selections = _uiState.value.variantSelections.values.toList()
        // Check required groups
        val missingRequired = item.variantGroups.filter { it.required }.any { group ->
            !_uiState.value.variantSelections.containsKey(group.id)
        }
        if (missingRequired) return
        addToCartDirect(item, selections)
        _uiState.update { it.copy(variantSelectorItem = null, variantSelections = emptyMap()) }
    }

    fun dismissVariantSelector() {
        _uiState.update { it.copy(variantSelectorItem = null, variantSelections = emptyMap()) }
    }

    private fun addToCartDirect(item: Item, variantSelections: List<VariantSelection>) {
        _uiState.update { state ->
            // For items with variants, each unique selection combo is a separate cart line
            val cartKey = item.id + variantSelections.sortedBy { it.groupName }.joinToString("|") { "${it.groupName}:${it.optionName}" }
            val existing = state.cart.find { cartItem ->
                val existingKey = cartItem.item.id + cartItem.variantSelections.sortedBy { it.groupName }.joinToString("|") { "${it.groupName}:${it.optionName}" }
                existingKey == cartKey
            }
            val newCart = if (existing != null) {
                state.cart.map { cartItem ->
                    val existingKey = cartItem.item.id + cartItem.variantSelections.sortedBy { it.groupName }.joinToString("|") { "${it.groupName}:${it.optionName}" }
                    if (existingKey == cartKey) cartItem.copy(quantity = cartItem.quantity + 1) else cartItem
                }
            } else {
                state.cart + CartItem(item = item, quantity = 1, variantSelections = variantSelections)
            }
            state.copy(cart = newCart)
        }
    }

    fun removeFromCart(cartIndex: Int) {
        AppLogger.d("POS", "Removing cart item at index=$cartIndex")
        _uiState.update { state ->
            state.copy(cart = state.cart.filterIndexed { index, _ -> index != cartIndex })
        }
    }

    fun removeFromCart(itemId: String) {
        AppLogger.d("POS", "Removing cart item by itemId=$itemId")
        _uiState.update { state ->
            state.copy(cart = state.cart.filter { it.item.id != itemId })
        }
    }

    fun updateCartQuantity(cartIndex: Int, quantity: Int) {
        AppLogger.d("POS", "Updating cart quantity: cartIndex=$cartIndex, quantity=$quantity")
        if (quantity <= 0) {
            removeFromCart(cartIndex)
            return
        }
        _uiState.update { state ->
            state.copy(cart = state.cart.mapIndexed { index, cartItem ->
                if (index == cartIndex) cartItem.copy(quantity = quantity) else cartItem
            })
        }
    }

    fun updateCartQuantity(itemId: String, quantity: Int) {
        AppLogger.d("POS", "Updating cart quantity: itemId=$itemId, quantity=$quantity")
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

    fun setScheduledFor(epochMs: Long?) {
        _uiState.update { it.copy(scheduledFor = epochMs) }
    }

    fun setChannel(channel: OrderChannel) {
        AppLogger.d("POS", "Setting channel: ${channel.name}")
        _uiState.update {
            it.copy(
                channel = channel,
                scheduledFor = if (channel == OrderChannel.PICKUP_LATER) it.scheduledFor else null,
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
        val zone = _uiState.value.taxPlaces.firstOrNull { it.id == taxPlaceId }
        AppLogger.i("POS-DELIVERY", "setSelectedTaxPlaceId: id=$taxPlaceId zone=${zone?.name} fee=${zone?.taxPercent}")
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
        AppLogger.i("POS", "User action: selected customer from dropdown: customerId=${customer.id}")
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
    fun updateDoctorName(name: String) { _uiState.update { it.copy(doctorName = name) } }
    fun updateDiagnosis(text: String) { _uiState.update { it.copy(diagnosis = text) } }
    fun dismissDrugInteractionWarning() { _uiState.update { it.copy(showDrugInteractionWarning = false) } }

    fun getSubtotal(): Double = _uiState.value.cart.fold(0.0) { acc, cartItem -> acc + cartItem.totalPrice }

    fun getOfferDiscount(): Double {
        val s = _uiState.value
        val offer = s.appliedOffer ?: return 0.0
        val subtotal = getSubtotal()
        // `discountValue` is the DISCOUNT AMOUNT, not the final price —
        // matching how the manager UI labels its number field
        // ("Discount Value / قيمة الخصم") and how the seed offer name
        // reads ("خصم ثابت" = "Fixed Discount"). Earlier this branch
        // computed `subtotal - discountValue` which interpreted the
        // value as the final fixed price, so a merchant who entered
        // 15 expecting to take 15 EGP off a 115 EGP combo accidentally
        // gave away 100 EGP (final = 15). Now the value is the amount
        // subtracted, capped at the subtotal so a too-generous offer
        // doesn't go negative.
        return when (offer.discountType) {
            "FIXED_PRICE" -> offer.discountValue.coerceAtMost(subtotal).coerceAtLeast(0.0)
            "PERCENT" -> subtotal * (offer.discountValue / 100.0)
            else -> 0.0
        }
    }

    fun getManualDiscount(): Double {
        val s = _uiState.value
        val value = s.manualDiscountValue.toDoubleOrNull() ?: return 0.0
        if (value <= 0) return 0.0
        val subtotal = getSubtotal()
        return when (s.manualDiscountType) {
            "PERCENT" -> subtotal * (value / 100.0)
            else -> value // FIXED
        }
    }

    fun getPointsDiscount(): Double {
        val s = _uiState.value
        return s.pointsToRedeem * s.pointsRedeemRate
    }

    fun getDiscountAmount(): Double = getOfferDiscount() + getManualDiscount() + getPointsDiscount()

    /**
     * Effective delivery fee for the current cart, based on the selected
     * delivery zone (TaxPlace). Zero when:
     *   • channel is not DELIVERY
     *   • cashier hasn't picked a zone yet
     *   • the picked zone has a 0 EGP fee (free delivery)
     *
     * The `taxPercent` column on TaxPlace is, despite the name, a flat
     * delivery fee in EGP (see TaxPlacesScreen — the manager UI labels
     * it "Delivery fee (EGP)"). This matches the backend logic in
     * OrderRoutes after the delivery-fee fix.
     */
    fun getDeliveryFee(): Double {
        val s = _uiState.value
        if (s.channel != OrderChannel.DELIVERY) {
            AppLogger.d("POS-DELIVERY", "getDeliveryFee: channel=${s.channel} → 0")
            return 0.0
        }
        // Priority matches the backend (OrderRoutes.kt:884):
        //   1. Selected delivery zone's flat fee (taxPercent column —
        //      misnamed, but it IS the flat EGP fee per zone).
        //   2. Vendor's default delivery fee.
        //   3. Zero.
        val zone = s.taxPlaces.firstOrNull { it.id == s.selectedTaxPlaceId }
        val fee = zone?.taxPercent ?: s.defaultDeliveryFee
        AppLogger.d("POS-DELIVERY", "getDeliveryFee: channel=DELIVERY selectedZoneId=${s.selectedTaxPlaceId} zonesLoaded=${s.taxPlaces.size} matchedZone=${zone?.name} zoneFee=${zone?.taxPercent} vendorDefault=${s.defaultDeliveryFee} → $fee")
        return fee
    }

    /**
     * Grand total including delivery fee. The confirmation screen needs
     * this so the cashier sees the same number the receipt will print.
     * Without `+ getDeliveryFee()`, the confirmation was understating the
     * total by the zone's fee (e.g. 100 EGP shown, 125 EGP charged).
     */
    fun getTotal(): Double =
        (getSubtotal() - getDiscountAmount() + getDeliveryFee()).coerceAtLeast(0.0)

    // ─── Customer methods ─────────────────────────────────────────

    fun selectCustomerAddress(addressId: String) {
        AppLogger.d("POS", "Selected customer address: addressId=$addressId")
        val address = _uiState.value.customerAddresses.find { it.id == addressId }
        _uiState.update {
            it.copy(
                selectedAddressId = addressId,
                clientAddress = address?.address.orEmpty(),
            )
        }
    }

    fun clearCustomer() {
        AppLogger.d("POS", "Clearing customer data")
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
        AppLogger.d("POS", "Reordering from history: orderId=${order.id}")
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

    // ─── Offers ────────────────────────────────────────────────────

    fun applyOffer(offer: Offer) {
        AppLogger.i("POS", "Applying offer: name=${offer.name}, discountType=${offer.discountType}, discountValue=${offer.discountValue}")
        val currentItems = _uiState.value.items
        val newCart = offer.items.mapNotNull { offerItem ->
            currentItems.find { it.id == offerItem.itemId }?.let { item ->
                CartItem(item = item, quantity = offerItem.quantity)
            }
        }
        if (newCart.isEmpty()) {
            AppLogger.w("POS", "No matching items found for offer ${offer.name}")
            return
        }
        _uiState.update { it.copy(cart = newCart, appliedOffer = offer) }
    }

    fun clearAppliedOffer() {
        _uiState.update { it.copy(appliedOffer = null) }
    }

    // ─── Manual Discount ──────────────────────────────────────────

    fun setManualDiscountValue(v: String) { _uiState.update { it.copy(manualDiscountValue = v) } }
    fun setManualDiscountType(type: String) { _uiState.update { it.copy(manualDiscountType = type) } }
    fun setManualDiscountReason(v: String) { _uiState.update { it.copy(manualDiscountReason = v) } }

    fun applyManualDiscount() {
        val s = _uiState.value
        val value = s.manualDiscountValue.toDoubleOrNull() ?: return
        if (value <= 0) return

        // Calculate effective discount percent
        val subtotal = getSubtotal()
        val effectivePercent = if (s.manualDiscountType == "PERCENT") value
        else if (subtotal > 0) (value / subtotal) * 100.0 else 0.0

        // Check if PIN approval is needed
        if (s.manualDiscountRequiresPin && effectivePercent > s.maxManualDiscountPercent && !s.pinApproved) {
            _uiState.update { it.copy(showPinDialog = true) }
            return
        }
        // Discount is applied — value is already in the state
        AppLogger.i("POS", "Manual discount applied: $value ${s.manualDiscountType}, reason=${s.manualDiscountReason}")
    }

    fun clearManualDiscount() {
        _uiState.update { it.copy(manualDiscountValue = "", manualDiscountType = "FIXED", manualDiscountReason = "", pinApproved = false) }
    }

    // ─── Loyalty Points ───────────────────────────────────────────

    fun applyAllPoints() {
        val s = _uiState.value
        val balance = s.selectedCustomer?.pointsBalance ?: 0
        if (balance >= s.minPointsRedeem) {
            _uiState.update { it.copy(pointsToRedeem = balance) }
            AppLogger.i("POS", "Applied all loyalty points: $balance")
        }
    }

    fun clearPointsRedemption() {
        _uiState.update { it.copy(pointsToRedeem = 0) }
    }

    // ─── PIN Approval ─────────────────────────────────────────────

    fun verifyManagerPin(pin: String) {
        viewModelScope.launch {
            try {
                // New endpoint: verify the PIN AND receive a short-lived approval token
                // that we'll attach to the next createOrder call. Old endpoint
                // (/workers/verify-manager-pin) only returned "ok" with no token.
                val resp = api.verifyOverridePin(
                    net.marllex.waselak.core.network.dto.VerifyOverridePinRequest(pin)
                )
                _uiState.update {
                    it.copy(
                        showPinDialog = false,
                        pinApproved = true,
                        pinError = null,
                        managerOverrideToken = resp.token,
                        managerApprovedByName = resp.managerName,
                    )
                }
                AppLogger.i("POS", "Manager PIN approved by ${resp.managerName} for discount")
            } catch (e: Exception) {
                _uiState.update { it.copy(pinError = e.message ?: "PIN غير صحيح") }
                AppLogger.w("POS", "Manager PIN verification failed: ${e.message}")
            }
        }
    }

    fun dismissPinDialog() {
        _uiState.update { it.copy(showPinDialog = false, pinError = null) }
    }

    // ─── Submit & Clear ───────────────────────────────────────────

    fun submitOrder(paymentMethod: PaymentMethod, paymentTiming: PaymentTiming = PaymentTiming.PAY_NOW, onSuccess: (Order) -> Unit) {
        val s = _uiState.value
        if (!s.canSubmit) return
        CrashReporter.logTransaction("create_order", "order")
        CrashReporter.setExtra("order.items_count", s.cart.size.toString())
        CrashReporter.setExtra("order.items_count", s.cart.size.toString())
        CrashReporter.setExtra("order.channel", s.channel.name)

        CrashReporter.logUserAction("submitOrder", "PosScreen", mapOf("channel" to s.channel.name, "items" to s.cart.size.toString(), "paymentMethod" to paymentMethod.name, "timing" to paymentTiming.name))
        AppLogger.d("POS", "Submitting order: channel=${s.channel.name}, items=${s.cart.size}, paymentMethod=${paymentMethod.name}")
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            // CREDIT payment requires customer with phone — auto-create if phone exists
            // If no customer info at all, just create order without credit tracking
            if (paymentMethod == PaymentMethod.CREDIT && s.clientPhone.isBlank() && s.selectedCustomer == null) {
                // Allow order creation without customer — no credit tracking, just a regular order marked as CREDIT
                AppLogger.w("POS", "CREDIT payment without customer — order will be created without credit tracking")
            }

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

            // Drug-interaction check, gated on real capability flags rather than the raw
            // business-type string (which used to silently miss non-uppercase values).
            if (s.canUseDrugInteractions && s.cart.size >= 2) {
                val itemIds = s.cart.map { it.item.id }
                drugInteractionRepository.checkInteractions(itemIds)
                    .onSuccess { result ->
                        if (result.hasInteractions && !s.showDrugInteractionWarning) {
                            _uiState.update { it.copy(
                                showDrugInteractionWarning = true,
                                drugInteractionResult = result,
                                isSubmitting = false,
                            ) }
                            return@launch
                        }
                    }
            }

            val orderItems = s.cart.map { cartItem ->
                CreateOrderItemRequest(
                    itemId = cartItem.item.id,
                    quantity = cartItem.quantity,
                    note = cartItem.note,
                    variantSelections = if (cartItem.variantSelections.isNotEmpty()) {
                        cartItem.variantSelections.map { vs ->
                            VariantSelectionRequest(
                                groupName = vs.groupName,
                                optionName = vs.optionName,
                                priceAdjustment = vs.priceAdjustment,
                            )
                        }
                    } else null,
                )
            }

            val clientAddress = when (s.channel) {
                OrderChannel.DELIVERY -> s.clientAddress.ifBlank { null }
                OrderChannel.TAKEAWAY -> s.clientAddress.ifBlank { null }
                OrderChannel.PICKUP_LATER -> s.clientAddress.ifBlank { null }
                else -> null
            }

            // Calculate total discount (offer + manual + points)
            val totalDiscount = getDiscountAmount()

            // Determine discount reason
            val reasons = mutableListOf<String>()
            if (s.appliedOffer != null) reasons.add("Offer: ${s.appliedOffer.name}")
            if (getManualDiscount() > 0) {
                val manualReason = s.manualDiscountReason.ifBlank { "Manual discount" }
                reasons.add(manualReason)
            }
            if (s.pointsToRedeem > 0) reasons.add("Points: ${s.pointsToRedeem}")
            val discountReason = reasons.joinToString("; ").ifBlank { null }

            // Route PICKUP_LATER with scheduledFor to scheduled orders API
            if (s.channel == OrderChannel.PICKUP_LATER && s.scheduledFor != null) {
                val scheduledItems = s.cart.map { cartItem ->
                    CreateScheduledOrderItemRequest(
                        itemId = cartItem.item.id,
                        quantity = cartItem.quantity,
                        note = cartItem.note,
                        variantOptions = if (cartItem.variantSelections.isNotEmpty()) {
                            cartItem.variantSelections.joinToString(", ") { "${it.groupName}: ${it.optionName}" }
                        } else null,
                    )
                }
                val request = CreateScheduledOrderRequest(
                    customerId = customerId,
                    clientName = s.clientName.ifBlank { null },
                    clientPhone = s.clientPhone.ifBlank { null },
                    channel = "PICKUP_LATER",
                    scheduledFor = s.scheduledFor,
                    notes = s.notes.ifBlank { null },
                    paymentMethod = paymentMethod.name,
                    discount = totalDiscount,
                    items = scheduledItems,
                )
                scheduledOrderRepository.createScheduledOrder(request)
                    .onSuccess { scheduled ->
                        AppLogger.i("POS", "Scheduled order created: id=${scheduled.id}")
                        // Create a stub Order to pass to onSuccess for receipt/navigation
                        val stubOrder = Order(
                            id = scheduled.id,
                            vendorId = scheduled.vendorId,
                            channel = OrderChannel.PICKUP_LATER,
                            status = OrderStatus.CREATED,
                            cashierId = "",
                            subtotal = scheduled.subtotal,
                            total = scheduled.total,
                            discount = scheduled.discount,
                            tax = scheduled.tax,
                            paymentMethod = paymentMethod,
                            paymentTiming = PaymentTiming.PAY_LATER,
                            createdAt = scheduled.createdAt,
                            updatedAt = scheduled.updatedAt,
                        )
                        onSuccess(stubOrder)
                        clearOrder()
                        _uiState.update { it.copy(isSubmitting = false, createdOrder = stubOrder) }
                    }
                    .onFailure { e ->
                    CrashReporter.captureException(e)
                        AppLogger.e("POS", "Scheduled order creation failed", e)
                        when {
                            e.isPlanLimitExceeded() -> _uiState.update {
                                it.copy(isSubmitting = false, showPlanLimitDialog = true, planLimitMessage = e.message ?: "")
                            }
                            e.isFeatureNotAvailableOrOffline() -> _uiState.update {
                                it.copy(isSubmitting = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "")
                            }
                            else -> _uiState.update { it.copy(isSubmitting = false, error = e.message) }
                        }
                    }
            } else {
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
                    reservationId = s.reservationId,
                    notes = s.notes.ifBlank { null },
                    items = orderItems,
                    discount = totalDiscount,
                    discountType = "FIXED",
                    offerId = s.appliedOffer?.id,
                    pointsRedeemed = s.pointsToRedeem,
                    discountReason = discountReason,
                    doctorName = s.doctorName.ifBlank { null },
                    diagnosis = s.diagnosis.ifBlank { null },
                    deliveryUserId = s.selectedDeliveryUserId,
                    // Attach the manager override token if a PIN was verified earlier
                    // in the flow. Server requires it when discount > 0 and the cashier
                    // isn't a manager; otherwise it's ignored.
                    managerOverrideToken = s.managerOverrideToken,
                    // Pre-computed delivery fee from the selected zone (or
                    // vendor default). Pass-through so the backend uses the
                    // exact same number the cashier saw on the cart sheet,
                    // AND so the offline path can apply it without needing
                    // its own TaxPlace lookup.
                    deliveryFee = getDeliveryFee(),
                ).onSuccess { order ->
                    AppLogger.i("POS", "Order submitted: id=${order.id}")
                    onSuccess(order)
                    clearOrder()
                    _uiState.update { it.copy(isSubmitting = false, createdOrder = order) }
                    if (s.channel == OrderChannel.DINE_IN && s.selectedTableId != null) {
                        tableRepository.refreshTables()
                    }
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("POS", "Order submission failed", e)
                    val msg = e.message.orEmpty()
                    when {
                        e.isPlanLimitExceeded() -> _uiState.update {
                            it.copy(isSubmitting = false, showPlanLimitDialog = true, planLimitMessage = e.message ?: "")
                        }
                        e.isFeatureNotAvailableOrOffline() -> _uiState.update {
                            it.copy(isSubmitting = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "")
                        }
                        // Server rejected the order because it needs manager approval that
                        // we didn't send (or sent an expired/invalid token). Re-open the PIN
                        // dialog and clear any stale approval state; cashier enters a fresh
                        // PIN and retries the submit.
                        msg.contains("DISCOUNT_REQUIRES_MANAGER") -> _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                showPinDialog = true,
                                pinError = "يرجى إدخال رمز المدير لاعتماد الخصم",
                                pinApproved = false,
                                managerOverrideToken = null,
                                managerApprovedByName = null,
                            )
                        }
                        else -> _uiState.update { it.copy(isSubmitting = false, error = e.message) }
                    }
                }
            }
        }
    }

    fun clearOrder() {
        AppLogger.d("POS", "Clearing order")
        phoneLookupJob?.cancel()
        searchDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                cart = emptyList(), createdOrder = null, appliedOffer = null,
                searchInput = "", searchQuery = "", scheduledFor = null,
                clientName = "", clientPhone = "", clientAddress = "",
                notes = "", selectedTableId = null, reservationId = null,
                selectedTaxPlaceId = _uiState.value.taxPlaces.firstOrNull { tp -> tp.isDefault }?.id,
                // Clear customer-related state
                selectedCustomer = null,
                customerAddresses = emptyList(),
                selectedAddressId = null,
                recentOrders = emptyList(),
                customerLookupDone = false,
                isLookingUpCustomer = false,
                phoneSearchResults = emptyList(),
                showPhoneDropdown = false,
                // Clear doctor/diagnosis & drug interactions
                doctorName = "",
                diagnosis = "",
                showDrugInteractionWarning = false,
                drugInteractionResult = null,
                // Clear manual discount & points
                manualDiscountValue = "",
                manualDiscountType = "FIXED",
                manualDiscountReason = "",
                pointsToRedeem = 0,
                // Clear manager approval state — tokens are single-order; force a fresh
                // PIN entry on the next order that needs approval.
                pinApproved = false,
                managerOverrideToken = null,
                managerApprovedByName = null,
            )
        }
    }

    // ─── Reservation Pre-fill ─────────────────────────────────────
    fun prefillFromReservation(
        tableId: String,
        reservationId: String,
        clientName: String,
        clientPhone: String?,
    ) {
        _uiState.update {
            it.copy(
                channel = OrderChannel.DINE_IN,
                selectedTableId = tableId,
                reservationId = reservationId,
                clientName = clientName,
                clientPhone = clientPhone ?: "",
            )
        }
    }

    // ─── Plan Limit / Feature Gating ─────────────────────────────
    fun dismissPlanLimitDialog() {
        _uiState.update { it.copy(showPlanLimitDialog = false, planLimitMessage = "") }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false, featureNotAvailableMessage = "") }
    }

    // ─── Barcode Scanner ─────────────────────────────────────────

    fun toggleBarcodeScanner() {
        AppLogger.d("POS", "Toggling barcode scanner")
        _uiState.update { it.copy(showBarcodeScanner = !it.showBarcodeScanner, barcodeScanMessage = null) }
    }

    fun dismissBarcodeScanMessage() {
        _uiState.update { it.copy(barcodeScanMessage = null) }
    }

    fun confirmOfflineMode() {
        AppLogger.i("POS", "User confirmed offline mode")
        offlineModeManager.confirmOfflineMode()
    }

    fun declineOfflineMode() {
        AppLogger.i("POS", "User declined offline mode")
        offlineModeManager.declineOfflineMode()
    }

    fun handleBarcodeScan(barcode: String) {
        AppLogger.d("POS", "Barcode scan: $barcode")
        viewModelScope.launch {
            // First try local DB lookup
            val item = itemRepository.getItemByBarcode(barcode).firstOrNull()
            if (item != null) {
                AppLogger.i("POS", "Barcode found: ${item.name}")
                addToCart(item)
                _uiState.update { it.copy(barcodeScanMessage = "✓ ${item.name}") }
            } else {
                AppLogger.w("POS", "Barcode not found: $barcode")
                _uiState.update { it.copy(barcodeScanMessage = "✗ Item not found: $barcode") }
            }
            // Auto-dismiss message after 2 seconds
            delay(2000)
            _uiState.update { it.copy(barcodeScanMessage = null) }
        }
    }
}
