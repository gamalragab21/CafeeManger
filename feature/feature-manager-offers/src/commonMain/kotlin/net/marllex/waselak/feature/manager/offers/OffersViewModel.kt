package net.marllex.waselak.feature.manager.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.OfferRepository
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Offer

data class SelectedOfferItem(
    val itemId: String,
    val itemName: String,
    val itemPrice: Double,
    val quantity: Int = 1,
)

class OffersViewModel(
    private val offerRepository: OfferRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    data class UiState(
        val offers: List<Offer> = emptyList(),
        val allItems: List<Item> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        // Form state
        val showForm: Boolean = false,
        val editingOffer: Offer? = null,
        val formName: String = "",
        val formDescription: String = "",
        val formImageUrl: String = "",
        val formDiscountType: String = "FIXED_PRICE",
        val formDiscountValue: String = "",
        val formActive: Boolean = true,
        val formExpiresAt: Long? = null,
        val formPromoCode: String = "",
        val formMaxUses: String = "",
        val formStartsAt: Long? = null,
        val formSelectedItems: List<SelectedOfferItem> = emptyList(),
        val isSaving: Boolean = false,
        val isUploadingImage: Boolean = false,
        // Delete confirmation
        val showDeleteConfirm: Offer? = null,
        // Item search
        val itemSearchQuery: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeOffers()
        loadData()
    }

    private fun observeOffers() {
        viewModelScope.launch {
            offerRepository.getOffers()
                .catch { e ->
                    AppLogger.e("OffersVM", "Error observing offers", e)
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { offers ->
                    _uiState.update { it.copy(offers = offers, isLoading = false) }
                }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                offerRepository.refreshOffers()
                itemRepository.refreshItems()
            } catch (e: Exception) {
                AppLogger.e("OffersVM", "Failed to load data", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        // Also observe items for the form
        viewModelScope.launch {
            itemRepository.getItems()
                .catch { e -> AppLogger.e("OffersVM", "Error observing items", e) }
                .collect { items ->
                    _uiState.update { it.copy(allItems = items) }
                }
        }
    }

    fun showAddForm() {
        _uiState.update {
            it.copy(
                showForm = true,
                editingOffer = null,
                formName = "",
                formDescription = "",
                formImageUrl = "",
                formDiscountType = "FIXED_PRICE",
                formDiscountValue = "",
                formActive = true,
                formExpiresAt = null,
                formPromoCode = "",
                formMaxUses = "",
                formStartsAt = null,
                formSelectedItems = emptyList(),
                itemSearchQuery = "",
            )
        }
    }

    fun showEditForm(offer: Offer) {
        _uiState.update {
            it.copy(
                showForm = true,
                editingOffer = offer,
                formName = offer.name,
                formDescription = offer.description ?: "",
                formImageUrl = offer.imageUrl ?: "",
                formDiscountType = offer.discountType,
                formDiscountValue = offer.discountValue.toString(),
                formActive = offer.active,
                formExpiresAt = offer.expiresAt,
                formPromoCode = offer.promoCode ?: "",
                formMaxUses = offer.maxUses?.toString() ?: "",
                formStartsAt = offer.startsAt,
                formSelectedItems = offer.items.map { item ->
                    SelectedOfferItem(
                        itemId = item.itemId,
                        itemName = item.itemName,
                        itemPrice = item.itemPrice,
                        quantity = item.quantity,
                    )
                },
                itemSearchQuery = "",
            )
        }
    }

    fun dismissForm() {
        _uiState.update { it.copy(showForm = false, editingOffer = null) }
    }

    fun updateFormName(value: String) = _uiState.update { it.copy(formName = value) }
    fun updateFormDescription(value: String) = _uiState.update { it.copy(formDescription = value) }
    fun updateFormDiscountType(value: String) = _uiState.update { it.copy(formDiscountType = value) }
    fun updateFormDiscountValue(value: String) = _uiState.update { it.copy(formDiscountValue = value) }
    fun updateFormActive(value: Boolean) = _uiState.update { it.copy(formActive = value) }
    fun updateFormExpiresAt(value: Long?) = _uiState.update { it.copy(formExpiresAt = value) }
    fun updateFormPromoCode(value: String) = _uiState.update { it.copy(formPromoCode = value) }
    fun updateFormMaxUses(value: String) = _uiState.update { it.copy(formMaxUses = value) }
    fun updateFormStartsAt(value: Long?) = _uiState.update { it.copy(formStartsAt = value) }
    fun updateItemSearchQuery(value: String) = _uiState.update { it.copy(itemSearchQuery = value) }

    fun uploadOfferImage(imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingImage = true) }
            offerRepository.uploadImage(imageBytes, "offer_${Clock.System.now().toEpochMilliseconds()}.jpg")
                .onSuccess { url ->
                    _uiState.update { it.copy(formImageUrl = url, isUploadingImage = false) }
                }
                .onFailure { e ->
                    AppLogger.e("OffersVM", "Failed to upload offer image", e)
                    _uiState.update { it.copy(isUploadingImage = false, error = e.message) }
                }
        }
    }

    fun removeOfferImage() {
        _uiState.update { it.copy(formImageUrl = "") }
    }

    fun addItemToOffer(item: Item) {
        _uiState.update { state ->
            val existing = state.formSelectedItems.find { it.itemId == item.id }
            if (existing != null) return@update state // Already added
            state.copy(
                formSelectedItems = state.formSelectedItems + SelectedOfferItem(
                    itemId = item.id,
                    itemName = item.name,
                    itemPrice = item.price,
                    quantity = 1,
                )
            )
        }
    }

    fun removeItemFromOffer(itemId: String) {
        _uiState.update { state ->
            state.copy(formSelectedItems = state.formSelectedItems.filter { it.itemId != itemId })
        }
    }

    fun updateItemQuantity(itemId: String, quantity: Int) {
        if (quantity < 1) return
        _uiState.update { state ->
            state.copy(
                formSelectedItems = state.formSelectedItems.map {
                    if (it.itemId == itemId) it.copy(quantity = quantity) else it
                }
            )
        }
    }

    fun saveOffer() {
        val state = _uiState.value
        if (state.formName.isBlank() || state.formSelectedItems.isEmpty()) return

        val discountValue = state.formDiscountValue.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val promoCode = state.formPromoCode.ifBlank { null }
            val maxUses = state.formMaxUses.toIntOrNull()
            val startsAt = state.formStartsAt
            val result = if (state.editingOffer != null) {
                offerRepository.updateOffer(
                    id = state.editingOffer.id,
                    name = state.formName,
                    description = state.formDescription.ifBlank { null },
                    imageUrl = state.formImageUrl.ifBlank { null },
                    discountType = state.formDiscountType,
                    discountValue = discountValue,
                    active = state.formActive,
                    expiresAt = state.formExpiresAt,
                    displayOrder = null,
                    items = state.formSelectedItems.map { it.itemId to it.quantity },
                    promoCode = promoCode,
                    maxUses = maxUses,
                    startsAt = startsAt,
                )
            } else {
                offerRepository.createOffer(
                    name = state.formName,
                    description = state.formDescription.ifBlank { null },
                    imageUrl = state.formImageUrl.ifBlank { null },
                    discountType = state.formDiscountType,
                    discountValue = discountValue,
                    active = state.formActive,
                    expiresAt = state.formExpiresAt,
                    displayOrder = 0,
                    items = state.formSelectedItems.map { it.itemId to it.quantity },
                    promoCode = promoCode,
                    maxUses = maxUses,
                    startsAt = startsAt,
                )
            }
            result.onSuccess {
                _uiState.update { it.copy(showForm = false, editingOffer = null, isSaving = false) }
            }.onFailure { e ->
                AppLogger.e("OffersVM", "Failed to save offer", e)
                _uiState.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }

    fun showDeleteConfirm(offer: Offer) {
        _uiState.update { it.copy(showDeleteConfirm = offer) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun confirmDelete() {
        val offer = _uiState.value.showDeleteConfirm ?: return
        viewModelScope.launch {
            offerRepository.deleteOffer(offer.id)
            _uiState.update { it.copy(showDeleteConfirm = null) }
        }
    }

    fun toggleOffer(offer: Offer) {
        viewModelScope.launch {
            offerRepository.toggleOffer(offer.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
