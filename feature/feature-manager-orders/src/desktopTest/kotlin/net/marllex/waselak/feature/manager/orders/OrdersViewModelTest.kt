package net.marllex.waselak.feature.manager.orders

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.PaginatedResult
import net.marllex.waselak.core.domain.repository.ReturnRepository
import net.marllex.waselak.core.domain.repository.TableRepository
import net.marllex.waselak.core.domain.repository.UserManagementRepository
import net.marllex.waselak.core.model.PaymentMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var userRepository: UserManagementRepository
    private lateinit var tableRepository: TableRepository
    private lateinit var viewModel: OrdersViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        orderRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        tableRepository = mockk(relaxed = true)

        // Default stubs: empty data, no errors
        every { orderRepository.getOrders(any(), any()) } returns flowOf(emptyList())
        every { userRepository.getUsers(any()) } returns flowOf(emptyList())
        every { tableRepository.getTables() } returns flowOf(emptyList())
        coEvery {
            orderRepository.refreshOrders(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns Result.success(PaginatedResult(emptyList(), 0, false))
        coEvery { userRepository.refreshUsers() } returns Result.success(emptyList())
        coEvery { tableRepository.refreshTables() } returns Result.success(emptyList())

        val returnRepository = mockk<ReturnRepository>()
        val itemRepository = mockk<ItemRepository>()
        viewModel = OrdersViewModel(orderRepository, userRepository, tableRepository, returnRepository, itemRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────

    @Test
    fun `initial state has no loading after init completes`() {
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `initial state has empty orders`() {
        assertTrue(viewModel.uiState.value.orders.isEmpty())
    }

    @Test
    fun `initial state has no error`() {
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `initial state has no active filters`() {
        assertFalse(viewModel.uiState.value.hasActiveFilters)
    }

    // ── Filter methods ──────────────────────────────────────────

    @Test
    fun `filterByStatus updates selected status`() {
        viewModel.filterByStatus("COMPLETED")
        assertEquals("COMPLETED", viewModel.uiState.value.selectedStatus)
    }

    @Test
    fun `filterByStatus with null clears status filter`() {
        viewModel.filterByStatus("COMPLETED")
        viewModel.filterByStatus(null)
        assertNull(viewModel.uiState.value.selectedStatus)
    }

    @Test
    fun `filterByChannel updates selected channel`() {
        viewModel.filterByChannel("DELIVERY")
        assertEquals("DELIVERY", viewModel.uiState.value.selectedChannel)
    }

    @Test
    fun `filterByCashier updates selected cashier`() {
        viewModel.filterByCashier("cashier-1")
        assertEquals("cashier-1", viewModel.uiState.value.selectedCashierId)
    }

    @Test
    fun `filterByDelivery updates selected delivery user`() {
        viewModel.filterByDelivery("delivery-1")
        assertEquals("delivery-1", viewModel.uiState.value.selectedDeliveryUserId)
    }

    @Test
    fun `filterByTable updates selected table`() {
        viewModel.filterByTable("table-1")
        assertEquals("table-1", viewModel.uiState.value.selectedTableId)
    }

    @Test
    fun `filterByDateRange updates date filters`() {
        viewModel.filterByDateRange(1000L, 2000L)
        assertEquals(1000L, viewModel.uiState.value.fromDate)
        assertEquals(2000L, viewModel.uiState.value.toDate)
    }

    @Test
    fun `hasActiveFilters is true when status filter set`() {
        viewModel.filterByStatus("COMPLETED")
        assertTrue(viewModel.uiState.value.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters is true when date range set`() {
        viewModel.filterByDateRange(1000L, null)
        assertTrue(viewModel.uiState.value.hasActiveFilters)
    }

    // ── clearAllFilters ────────────────────────────────────────

    @Test
    fun `clearAllFilters resets all filter fields`() {
        viewModel.filterByStatus("COMPLETED")
        viewModel.filterByChannel("DELIVERY")
        viewModel.filterByCashier("cashier-1")
        viewModel.filterByDelivery("delivery-1")
        viewModel.filterByTable("table-1")
        viewModel.filterByDateRange(1000L, 2000L)
        assertTrue(viewModel.uiState.value.hasActiveFilters)

        viewModel.clearAllFilters()
        val state = viewModel.uiState.value
        assertNull(state.selectedStatus)
        assertNull(state.selectedChannel)
        assertNull(state.selectedCashierId)
        assertNull(state.selectedDeliveryUserId)
        assertNull(state.selectedTableId)
        assertNull(state.fromDate)
        assertNull(state.toDate)
        assertFalse(state.hasActiveFilters)
    }

    // ── Dialog state ───────────────────────────────────────────

    @Test
    fun `dismissAssignDeliveryDialog clears dialog state`() {
        viewModel.dismissAssignDeliveryDialog()
        assertFalse(viewModel.uiState.value.showAssignDeliveryDialog)
        assertNull(viewModel.uiState.value.assignOrderId)
    }

    @Test
    fun `dismissEditOrderDialog clears dialog state`() {
        viewModel.dismissEditOrderDialog()
        assertFalse(viewModel.uiState.value.showEditOrderDialog)
        assertNull(viewModel.uiState.value.editingOrder)
    }

    @Test
    fun `dismissPaymentDialog clears dialog state`() {
        viewModel.dismissPaymentDialog()
        assertFalse(viewModel.uiState.value.showPaymentDialog)
        assertNull(viewModel.uiState.value.payingOrder)
    }

    @Test
    fun `dismissRefundDialog clears dialog state`() {
        viewModel.dismissRefundDialog()
        assertFalse(viewModel.uiState.value.showRefundDialog)
        assertNull(viewModel.uiState.value.refundingOrder)
        assertEquals("", viewModel.uiState.value.refundReason)
    }

    // ── Edit field updates ──────────────────────────────────────

    @Test
    fun `updateEditClientName updates state`() {
        viewModel.updateEditClientName("Ahmed")
        assertEquals("Ahmed", viewModel.uiState.value.editClientName)
    }

    @Test
    fun `updateEditClientPhone updates state`() {
        viewModel.updateEditClientPhone("01234567890")
        assertEquals("01234567890", viewModel.uiState.value.editClientPhone)
    }

    @Test
    fun `updateEditClientAddress updates state`() {
        viewModel.updateEditClientAddress("123 Main St")
        assertEquals("123 Main St", viewModel.uiState.value.editClientAddress)
    }

    @Test
    fun `updateEditNotes updates state`() {
        viewModel.updateEditNotes("Extra sugar")
        assertEquals("Extra sugar", viewModel.uiState.value.editNotes)
    }

    // ── Payment method ──────────────────────────────────────────

    @Test
    fun `selectPaymentMethod updates state`() {
        viewModel.selectPaymentMethod(PaymentMethod.CARD)
        assertEquals(PaymentMethod.CARD, viewModel.uiState.value.selectedPaymentMethod)
    }

    // ── Refund reason ───────────────────────────────────────────

    @Test
    fun `updateRefundReason updates state`() {
        viewModel.updateRefundReason("Damaged item")
        assertEquals("Damaged item", viewModel.uiState.value.refundReason)
    }

    // ── Error handling ──────────────────────────────────────────

    @Test
    fun `clearError sets error to null`() {
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadOrders with failure sets error`() {
        coEvery {
            orderRepository.refreshOrders(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns Result.failure(Exception("Network error"))

        viewModel.loadOrders()
        // userFriendlyMessage() converts generic Exception to Arabic error
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── Channel filter resets incompatible status ───────────────

    @Test
    fun `filterByChannel resets incompatible status`() {
        // SERVED is only valid for DINE_IN, not for IN_STORE
        viewModel.filterByStatus("SERVED")
        assertEquals("SERVED", viewModel.uiState.value.selectedStatus)

        viewModel.filterByChannel("IN_STORE")
        // SERVED is not in IN_STORE available statuses, so it should be reset
        assertNull(viewModel.uiState.value.selectedStatus)
        assertEquals("IN_STORE", viewModel.uiState.value.selectedChannel)
    }

    @Test
    fun `filterByChannel keeps compatible status`() {
        viewModel.filterByStatus("COMPLETED")
        viewModel.filterByChannel("DINE_IN")
        // COMPLETED is valid for DINE_IN, so it should be kept
        assertEquals("COMPLETED", viewModel.uiState.value.selectedStatus)
    }
}
