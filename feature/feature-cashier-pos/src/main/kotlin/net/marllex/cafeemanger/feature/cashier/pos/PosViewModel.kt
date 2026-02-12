package net.marllex.cafeemanger.feature.cashier.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.CategoryRepository
import net.marllex.cafeemanger.core.domain.repository.ItemRepository
import net.marllex.cafeemanger.core.domain.repository.OrderRepository
import net.marllex.cafeemanger.core.domain.repository.TableRepository
import net.marllex.cafeemanger.core.domain.repository.TaxPlaceRepository
import net.marllex.cafeemanger.core.domain.repository.VendorRepository
import net.marllex.cafeemanger.core.model.CartItem
import net.marllex.cafeemanger.core.model.Category
import net.marllex.cafeemanger.core.model.Item
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderChannel
import net.marllex.cafeemanger.core.model.PaymentMethod
import net.marllex.cafeemanger.core.model.Table
import net.marllex.cafeemanger.core.model.TaxPlace
import net.marllex.cafeemanger.core.network.dto.CreateOrderItemRequest
import javax.inject.Inject

@HiltViewModel
class PosViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository,
    private val taxPlaceRepository: TaxPlaceRepository,
    private val vendorRepository: VendorRepository,
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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadMenu() }

    fun loadMenu() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Load vendor feature flags
            vendorRepository.refreshVendor().onSuccess { vendor ->
                val defaultChannel = when {
                    vendor.enableDineIn -> OrderChannel.DINE_IN
                    vendor.enableDelivery -> OrderChannel.DELIVERY
                    else -> OrderChannel.DINE_IN
                }
                _uiState.update { it.copy(
                    enableTables = vendor.enableTables,
                    enableDineIn = vendor.enableDineIn,
                    enableDelivery = vendor.enableDelivery,
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
                selectedTaxPlaceId = if (channel == OrderChannel.DELIVERY) it.selectedTaxPlaceId ?: it.taxPlaces.firstOrNull { tp -> tp.isDefault }?.id else null
            )
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
    fun setClientPhone(v: String) { _uiState.update { it.copy(clientPhone = v) } }
    fun setClientAddress(v: String) { _uiState.update { it.copy(clientAddress = v) } }
    fun setNotes(v: String) { _uiState.update { it.copy(notes = v) } }

    fun getSubtotal(): Double = _uiState.value.cart.fold(0.0) { acc, cartItem -> acc + cartItem.item.price * cartItem.quantity }

    fun submitOrder(paymentMethod: PaymentMethod, onSuccess: (Order) -> Unit) {
        val s = _uiState.value
        if (s.cart.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val orderItems = s.cart.map { cartItem ->
                CreateOrderItemRequest(
                    itemId = cartItem.item.id,
                    quantity = cartItem.quantity,
                    note = cartItem.note,
                )
            }

            orderRepository.createOrder(
                channel = s.channel,
                tableId = if (s.channel == OrderChannel.DINE_IN) s.selectedTableId else null,
                clientName = s.clientName.ifBlank { null },
                clientPhone = s.clientPhone.ifBlank { null },
                clientAddress = if (s.channel == OrderChannel.DELIVERY) s.clientAddress.ifBlank { null } else null,
                geoLat = null, geoLng = null,
                paymentMethod = paymentMethod,
                taxPlaceId = if (s.channel == OrderChannel.DELIVERY) s.selectedTaxPlaceId else null,
                notes = s.notes.ifBlank { null },
                items = orderItems,
            ).onSuccess { order ->
                _uiState.update { it.copy(isSubmitting = false, createdOrder = order, cart = emptyList()) }
                // Table is automatically set to OCCUPIED by backend for dine-in orders
                // Refresh tables to reflect the new status locally
                if (s.channel == OrderChannel.DINE_IN && s.selectedTableId != null) {
                    tableRepository.refreshTables()
                }
                onSuccess(order)
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }

    fun clearOrder() {
        _uiState.update {
            it.copy(
                cart = emptyList(), createdOrder = null,
                clientName = "", clientPhone = "", clientAddress = "",
                notes = "", selectedTableId = null, selectedTaxPlaceId = _uiState.value.taxPlaces.firstOrNull { it.isDefault }?.id,
            )
        }
    }
}
