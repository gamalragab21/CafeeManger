package net.marllex.waselak.feature.manager.staff

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.staff.generated.resources.Res
import net.marllex.waselak.feature.manager.staff.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.Attendance
import net.marllex.waselak.core.model.AttendanceSummary
import net.marllex.waselak.core.model.Overtime
import net.marllex.waselak.core.model.SalaryPayment
import net.marllex.waselak.core.model.SalaryType
import net.marllex.waselak.core.model.Worker
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.FeatureNotAvailableView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.ProfileAvatar
import net.marllex.waselak.core.ui.components.ShiftSummaryBottomSheet
import androidx.compose.material.icons.filled.PointOfSale
import net.marllex.waselak.core.ui.platform.rememberImagePickerLauncher
import androidx.compose.material.icons.filled.CameraAlt
import org.koin.compose.viewmodel.koinViewModel
import net.marllex.waselak.core.common.extensions.formatEpochMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    onNavigateToWorkerQrCode: (String) -> Unit = {},
    isAttendanceEnabled: Boolean = true,
    isSalaryEnabled: Boolean = true,
    isOvertimeEnabled: Boolean = true,
    isDeliveryEnabled: Boolean = true,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: StaffViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    // Sub-screen state: null = main tabs, "delivery" or "announcements"
    var activeSubScreen by remember { mutableStateOf<String?>(null) }
    val tabs = listOf(
        stringResource(Res.string.workers),
        stringResource(Res.string.attendance),
        stringResource(Res.string.salary),
        stringResource(Res.string.overtime),
        stringResource(Res.string.roles_settings),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (activeSubScreen) {
                            "delivery" -> stringResource(Res.string.delivery_dashboard)
                            "announcements" -> stringResource(Res.string.announcements)
                            else -> stringResource(Res.string.staff_management)
                        }
                    )
                },
                navigationIcon = {
                    if (activeSubScreen != null) {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                        }
                    } else if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (activeSubScreen) {
                "delivery" -> DeliveryDashboardScreen()
                "announcements" -> AnnouncementsScreen(isManager = true)
                else -> {
                    // Quick Access cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isDeliveryEnabled) {
                            Card(
                                onClick = { activeSubScreen = "delivery" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.DeliveryDining,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Column {
                                        Text(
                                            stringResource(Res.string.delivery_dashboard),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Text(
                                            stringResource(Res.string.view_delivery_dashboard),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            onClick = { activeSubScreen = "announcements" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Campaign,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Column {
                                    Text(
                                        stringResource(Res.string.announcements),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Text(
                                        stringResource(Res.string.view_announcements),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // 5-tab ScrollableTabRow
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 8.dp,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Tab(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }

                    when {
                        uiState.isLoading -> LoadingIndicator()
                        uiState.error != null && uiState.workers.isEmpty() -> ErrorView(
                            message = uiState.error!!, onRetry = viewModel::loadData
                        )

                        else -> Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                0 -> WorkersTab(uiState, viewModel, onNavigateToWorkerQrCode)
                                1 -> if (isAttendanceEnabled) AttendanceTab(uiState, viewModel) else FeatureNotAvailableView()
                                2 -> if (isSalaryEnabled) SalaryTab(uiState, viewModel) else FeatureNotAvailableView()
                                3 -> if (isOvertimeEnabled) OvertimeTab(uiState, viewModel) else FeatureNotAvailableView()
                                4 -> RolesTab(uiState, viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet for Add/Edit Worker
        if (uiState.showAddWorkerDialog) {
            AddEditWorkerBottomSheet(uiState, viewModel)
        }
        if (uiState.showDeleteWorkerDialog) {
            DeleteConfirmDialog(
                title = stringResource(Res.string.delete_worker),
                message = stringResource(Res.string.delete_worker_confirm),
                onConfirm = viewModel::confirmDeleteWorker,
                onDismiss = viewModel::dismissDeleteWorkerDialog,
            )
        }
        if (uiState.showAddRoleDialog) {
            AddRoleDialog(uiState, viewModel)
        }
        if (uiState.showDeleteRoleDialog) {
            DeleteConfirmDialog(
                title = stringResource(Res.string.delete_role),
                message = stringResource(Res.string.delete_role_confirm),
                onConfirm = viewModel::confirmDeleteRole,
                onDismiss = viewModel::dismissDeleteRoleDialog,
            )
        }
        if (uiState.showBatchPayDialog) {
            BatchPayNoteDialog(uiState, viewModel)
        }
        if (uiState.showAddOvertimeDialog) {
            AddOvertimeDialog(uiState, viewModel)
        }
        if (uiState.showEditRateDialog) {
            EditRateDialog(uiState, viewModel)
        }
        if (uiState.showPlanLimitDialog) {
            net.marllex.waselak.core.ui.components.PlanLimitBottomSheet(
                message = uiState.planLimitMessage,
                onDismiss = viewModel::dismissPlanLimitDialog,
            )
        }
        if (uiState.showShiftSummary) {
            ShiftSummaryBottomSheet(
                shiftSummary = uiState.shiftSummaryData,
                isLoading = uiState.shiftSummaryLoading,
                error = uiState.shiftSummaryError,
                onRetry = {
                    val worker = uiState.workers.firstOrNull { it.fullName == uiState.shiftSummaryWorkerName }
                    if (worker != null) viewModel.fetchShiftSummary(worker)
                },
                onDismiss = viewModel::dismissShiftSummary,
            )
        }
    }
}

// ─── Workers Tab ─────────────────────────────────────────────────

@Composable
private fun WorkersTab(
    uiState: StaffViewModel.UiState,
    viewModel: StaffViewModel,
    onNavigateToWorkerQrCode: (String) -> Unit,
) {
    val filteredWorkers = remember(uiState.workers, uiState.selectedRoleFilter) {
        if (uiState.selectedRoleFilter == null) uiState.workers
        else uiState.workers.filter { it.role == uiState.selectedRoleFilter }
    }

    val uniqueRoles = remember(uiState.workers) {
        uiState.workers.map { it.role }.distinct().sorted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.workers.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PersonAdd,
                message = stringResource(Res.string.no_workers),
            )
        } else {
            Column {
                // Role filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedRoleFilter == null,
                            onClick = { viewModel.filterByRole(null) },
                            label = { Text(stringResource(Res.string.all)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                    items(uniqueRoles) { role ->
                        FilterChip(
                            selected = uiState.selectedRoleFilter == role,
                            onClick = { viewModel.filterByRole(role) },
                            label = { Text(role) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredWorkers, key = { it.id }) { worker ->
                        WorkerCard(
                            worker = worker,
                            isPresent = uiState.todaySummary.find { it.workerId == worker.id }?.presentToday
                                ?: false,
                            onEdit = { viewModel.showAddWorkerDialog(worker) },
                            onToggleActive = { viewModel.toggleWorkerActive(worker) },
                            onDelete = { viewModel.showDeleteWorkerConfirm(worker) },
                            onViewQrCode = { onNavigateToWorkerQrCode(worker.id) },
                            onViewShiftSummary = if (worker.isLoginEnabled && worker.userId != null) {
                                { viewModel.fetchShiftSummary(worker) }
                            } else null,
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.showAddWorkerDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.add_worker))
        }
    }
}

@Composable
private fun WorkerCard(
    worker: Worker,
    isPresent: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onViewQrCode: () -> Unit,
    onViewShiftSummary: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (worker.active) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            1.dp,
            if (isPresent) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Profile/Presence Section
                Box(contentAlignment = Alignment.BottomEnd) {
                    ProfileAvatar(
                        photoUrl = worker.photoUrl,
                        size = 48.dp,
                        contentDescription = worker.fullName,
                    )
                    // Presence Dot
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPresent) MaterialTheme.colorScheme.primary // Success
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = worker.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (worker.active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ID: ${worker.workerId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(" • ", color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = worker.role,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Quick Action + Menu
                Row {
                    if (onViewShiftSummary != null) {
                        IconButton(onClick = onViewShiftSummary) {
                            Icon(Icons.Default.PointOfSale, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onViewQrCode) {
                        Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit Details") },
                                onClick = { onEdit(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (worker.active) "Deactivate" else "Activate") },
                                onClick = { onToggleActive(); showMenu = false },
                                leadingIcon = { Icon(if (worker.active) Icons.Default.PersonOff else Icons.Default.PersonAdd, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { onDelete(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bottom Section: Status Tags & Salary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (worker.isLoginEnabled) {
                        StatusTag(label = "Login Access", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (!worker.active) {
                        StatusTag(label = "Inactive", color = MaterialTheme.colorScheme.error)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when (worker.salaryType) {
                            SalaryType.DAILY -> "Daily Salary"
                            SalaryType.WEEKLY -> "Weekly Salary"
                            SalaryType.MONTHLY -> "Monthly Salary"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${worker.salaryAmount} EGP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StatusTag(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Attendance Tab ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceTab(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    val filteredSummary = viewModel.filteredTodaySummary
    val presentCount = filteredSummary.count { it.presentToday }
    val awayCount = filteredSummary.count { !it.presentToday && it.attendedToday }
    val absentCount = filteredSummary.count { !it.presentToday && !it.attendedToday }
    val totalCount = filteredSummary.size

    // Build a lookup map: workerId -> Worker (for checking isLoginEnabled / role)
    val workerLookup = remember(uiState.workers) {
        uiState.workers.associateBy { it.id }
    }

    // Collect unique roles from today's summary + attendance records
    val availableRoles = remember(uiState.todaySummary, uiState.attendanceRecords) {
        val summaryRoles = uiState.todaySummary.map { it.workerRole }
        val recordRoles = uiState.attendanceRecords.mapNotNull { it.workerRole }
        (summaryRoles + recordRoles).distinct().sorted()
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ─── Today's Overview Card ───────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.who_is_working),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Total
                        AttendanceStatCard(
                            count = totalCount,
                            label = stringResource(Res.string.all),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // Present
                        AttendanceStatCard(
                            count = presentCount,
                            label = stringResource(Res.string.present),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // Away/Left
                        AttendanceStatCard(
                            count = awayCount,
                            label = stringResource(Res.string.away),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // Absent
                        AttendanceStatCard(
                            count = absentCount,
                            label = stringResource(Res.string.absent),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ─── Role Filter Chips ──────────────────────────────────
        if (availableRoles.size > 1) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = uiState.attendanceRoleFilter == null,
                            onClick = { viewModel.setAttendanceRoleFilter(null) },
                            label = { Text(stringResource(Res.string.all)) },
                            leadingIcon = if (uiState.attendanceRoleFilter == null) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                    items(availableRoles) { role ->
                        FilterChip(
                            selected = uiState.attendanceRoleFilter.equals(role, ignoreCase = true),
                            onClick = {
                                viewModel.setAttendanceRoleFilter(
                                    if (uiState.attendanceRoleFilter.equals(role, ignoreCase = true)) null else role
                                )
                            },
                            label = { Text(role) },
                            leadingIcon = if (uiState.attendanceRoleFilter.equals(role, ignoreCase = true)) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        }

        // ─── Worker Summary Cards (Today) ────────────────────────
        items(filteredSummary) { summary ->
            val worker = workerLookup[summary.workerId]
            val isAppUser = worker?.isLoginEnabled == true

            WorkerAttendanceSummaryCard(
                summary = summary,
                isAppUser = isAppUser,
                workerRole = summary.workerRole,
                photoUrl = worker?.photoUrl,
            )
        }

        // ─── Attendance History Section ──────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.EventBusy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.attendance_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Period Filter (Today / Week / Month / Custom)
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val periods = listOf(
                    StaffViewModel.AttendancePeriod.TODAY to stringResource(Res.string.today),
                    StaffViewModel.AttendancePeriod.WEEK to stringResource(Res.string.this_week),
                    StaffViewModel.AttendancePeriod.MONTH to stringResource(Res.string.this_month),
                    StaffViewModel.AttendancePeriod.CUSTOM to stringResource(Res.string.custom_range),
                )
                periods.forEachIndexed { index, (period, label) ->
                    SegmentedButton(
                        selected = uiState.attendancePeriod == period,
                        onClick = { viewModel.setAttendancePeriod(period) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Custom date range inputs
        if (uiState.attendancePeriod == StaffViewModel.AttendancePeriod.CUSTOM) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.attendanceFromDate,
                        onValueChange = viewModel::setAttendanceFromDate,
                        label = { Text(stringResource(Res.string.from_date)) },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = uiState.attendanceToDate,
                        onValueChange = viewModel::setAttendanceToDate,
                        label = { Text(stringResource(Res.string.to_date)) },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        // Status + Worker Filters
        item {
            AttendanceFilters(
                workers = uiState.workers,
                selectedWorkerId = uiState.attendanceWorkerFilter,
                statusFilter = uiState.attendanceStatusFilter,
                onWorkerSelected = viewModel::setAttendanceWorkerFilter,
                onStatusFilterChanged = viewModel::setAttendanceStatusFilter,
            )
        }

        val filteredRecords = viewModel.filteredAttendanceRecords

        if (filteredRecords.isNotEmpty()) {
            items(filteredRecords) { record ->
                val recordWorker = workerLookup[record.workerId]
                AttendanceRecordCard(record, photoUrl = recordWorker?.photoUrl)
            }
        }

        if (filteredSummary.isEmpty() && filteredRecords.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.EventBusy,
                    message = stringResource(Res.string.no_attendance_records),
                )
            }
        }

        // Bottom spacing
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Worker Attendance Summary Card (Today's overview per worker) ──

@Composable
private fun WorkerAttendanceSummaryCard(
    summary: AttendanceSummary,
    isAppUser: Boolean,
    workerRole: String,
    photoUrl: String? = null,
) {
    // Three states:
    // 1. Present (presentToday=true): green — currently working
    // 2. Away/Left (!presentToday && attendedToday): orange — attended but left
    // 3. Absent (!presentToday && !attendedToday): red — never showed up
    val isAway = !summary.presentToday && summary.attendedToday

    val statusColor = when {
        summary.presentToday -> MaterialTheme.colorScheme.primary
        isAway -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val statusLabel = when {
        summary.presentToday -> stringResource(Res.string.present)
        isAway -> stringResource(Res.string.away)
        else -> stringResource(Res.string.absent)
    }

    val borderColor = when {
        summary.presentToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isAway -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar with status indicator
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(
                    photoUrl = photoUrl,
                    size = 44.dp,
                    contentDescription = summary.workerName,
                )
                // Online/Offline dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Name + Role + Tags
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.workerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Role badge
                    val roleColor = when (workerRole.lowercase()) {
                        "cashier" -> MaterialTheme.colorScheme.tertiary
                        "delivery" -> MaterialTheme.colorScheme.secondary
                        "manager" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = roleColor.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = workerRole,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = roleColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    // Auto-tracked badge for app users
                    if (isAppUser) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = stringResource(Res.string.auto_login),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Status chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }
        }
    }
}

// ─── Attendance Stats Card ──────────────────────────────────────

@Composable
private fun AttendanceStatCard(
    count: Int,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

// ─── Attendance Filters ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceFilters(
    workers: List<Worker>,
    selectedWorkerId: String?,
    statusFilter: String?,
    onWorkerSelected: (String?) -> Unit,
    onStatusFilterChanged: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Worker filter dropdown
        var workerExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = workerExpanded,
            onExpandedChange = { workerExpanded = it },
        ) {
            OutlinedTextField(
                value = workers.find { it.id == selectedWorkerId }?.fullName ?: stringResource(Res.string.all_workers),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.filter_by_worker)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workerExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(
                expanded = workerExpanded,
                onDismissRequest = { workerExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.all_workers)) },
                    onClick = {
                        onWorkerSelected(null)
                        workerExpanded = false
                    },
                )
                workers.forEach { worker ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(worker.fullName)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = worker.role,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                                if (worker.isLoginEnabled) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.has_app_access),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            onWorkerSelected(worker.id)
                            workerExpanded = false
                        },
                    )
                }
            }
        }

        // Status filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { onStatusFilterChanged(null) },
                label = { Text(stringResource(Res.string.all)) },
                leadingIcon = if (statusFilter == null) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
            )
            FilterChip(
                selected = statusFilter == "PRESENT",
                onClick = { onStatusFilterChanged(if (statusFilter == "PRESENT") null else "PRESENT") },
                label = { Text(stringResource(Res.string.present)) },
                leadingIcon = if (statusFilter == "PRESENT") {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
            FilterChip(
                selected = statusFilter == "ABSENT",
                onClick = { onStatusFilterChanged(if (statusFilter == "ABSENT") null else "ABSENT") },
                label = { Text(stringResource(Res.string.absent)) },
                leadingIcon = if (statusFilter == "ABSENT") {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            )
        }
    }
}

// ─── Attendance Record Card (History) ────────────────────────────

@Composable
private fun AttendanceRecordCard(record: Attendance, photoUrl: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Top Row: Name + Role + Auth Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Worker avatar
                ProfileAvatar(
                    photoUrl = photoUrl,
                    size = 38.dp,
                    contentDescription = record.workerName,
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.workerName ?: stringResource(Res.string.worker),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Date
                        Text(
                            text = record.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Role badge
                        if (!record.workerRole.isNullOrBlank()) {
                            val roleColor = when (record.workerRole!!.lowercase()) {
                                "cashier" -> MaterialTheme.colorScheme.tertiary
                                "delivery" -> MaterialTheme.colorScheme.secondary
                                "manager" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = roleColor.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    text = record.workerRole!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = roleColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        // Auth method badge
                        val authLabel = when (record.authMethod) {
                            "AUTO" -> stringResource(Res.string.auth_auto)
                            "PIN" -> "PIN"
                            "QR" -> "QR"
                            else -> null
                        }
                        if (authLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (record.authMethod) {
                                    "AUTO" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                Text(
                                    text = authLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = when (record.authMethod) {
                                        "AUTO" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            // Bottom Row: Check-in / Check-out / Worked
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Check In
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.checked_in),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = record.checkIn.formatEpochMs("hh:mm a"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Check Out
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.checked_out),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    val checkOutTime = record.checkOut
                    if (checkOutTime != null) {
                        Text(
                            text = checkOutTime.formatEpochMs("hh:mm a"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = "-- : --",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Worked
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.worked_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = record.workedMinutes?.let { record.workedHoursFormatted } ?: "-- : --",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ─── Salary Tab ──────────────────────────────────────────────────

@Composable
private fun SalaryTab(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    if (uiState.selectedSalaryWorkerId == null) {
        WorkerSalaryListView(uiState, viewModel)
    } else {
        WorkerSalaryDetailView(uiState, viewModel, uiState.selectedSalaryWorkerId)
    }
}

@Composable
private fun WorkerSalaryListView(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    data class WorkerSummary(
        val workerId: String,
        val workerName: String,
        val workerPhotoUrl: String?,
        val workerRole: String,
        val salaryType: String,
        val unpaidAmount: Double,
        val unpaidCount: Int,
    )

    val workerSummaries = remember(uiState.salaryPayments, uiState.workers, uiState.salaryPaidFilter) {
        val grouped = uiState.salaryPayments.groupBy { it.workerId }
        uiState.workers.mapNotNull { worker ->
            val payments = grouped[worker.id] ?: emptyList()
            val unpaid = payments.filter { !it.paid }
            val summary = WorkerSummary(
                workerId = worker.id,
                workerName = worker.fullName,
                workerPhotoUrl = worker.photoUrl,
                workerRole = worker.role,
                salaryType = worker.salaryType.name,
                unpaidAmount = unpaid.sumOf { it.totalAmount },
                unpaidCount = unpaid.size,
            )
            when (uiState.salaryPaidFilter) {
                false -> if (summary.unpaidCount > 0) summary else null
                true -> if (payments.any { it.paid }) summary else null
                null -> summary
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Filter Section with a subtle background
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.salaryPaidFilter == null,
                    onClick = { viewModel.filterSalaryByPaid(null) },
                    label = { Text(stringResource(Res.string.all)) },
                    leadingIcon = { if(uiState.salaryPaidFilter == null) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = uiState.salaryPaidFilter == false,
                    onClick = { viewModel.filterSalaryByPaid(false) },
                    label = { Text(stringResource(Res.string.unpaid)) },
                    leadingIcon = { if(uiState.salaryPaidFilter == false) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = uiState.salaryPaidFilter == true,
                    onClick = { viewModel.filterSalaryByPaid(true) },
                    label = { Text(stringResource(Res.string.paid)) },
                    leadingIcon = { if(uiState.salaryPaidFilter == true) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                )
            }
        }

        if (workerSummaries.isEmpty()) {
            EmptyState(icon = Icons.Rounded.Payments, message = stringResource(Res.string.no_salary_records))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(workerSummaries, key = { it.workerId }) { summary ->
                    OutlinedCard( // Outlined looks cleaner in lists
                        onClick = { viewModel.selectWorkerForSalary(summary.workerId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (summary.unpaidCount > 0)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileAvatar(
                                photoUrl = summary.workerPhotoUrl,
                                size = 44.dp,
                                contentDescription = summary.workerName,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.workerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = summary.workerRole,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        text = " • ${summary.salaryType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (summary.unpaidCount > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${summary.unpaidAmount.toInt()} ${stringResource(Res.string.egp)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "${summary.unpaidCount} bills",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerSalaryDetailView(uiState: StaffViewModel.UiState, viewModel: StaffViewModel, workerId: String) {
    val worker = uiState.workers.find { it.id == workerId }
    val payments = remember(uiState.salaryPayments, uiState.salaryPaidFilter, workerId) {
        val filtered = uiState.salaryPayments.filter { it.workerId == workerId }
        when (uiState.salaryPaidFilter) {
            true -> filtered.filter { it.paid }
            false -> filtered.filter { !it.paid }
            null -> filtered
        }.sortedByDescending { it.periodStart }
    }
    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.selectWorkerForSalary(null) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                        }
                        ProfileAvatar(
                            photoUrl = worker?.photoUrl,
                            size = 40.dp,
                            contentDescription = worker?.fullName,
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(worker?.fullName ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(worker?.role ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (uiState.selectedPaymentIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::showBatchPayDialog,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.Payments, null) },
                    text = { Text("Pay ${uiState.selectedPaymentIds.size} Items") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Filter chips
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.salaryPaidFilter == null,
                    onClick = { viewModel.filterSalaryByPaid(null) },
                    label = { Text(stringResource(Res.string.all)) },
                )
                FilterChip(
                    selected = uiState.salaryPaidFilter == false,
                    onClick = { viewModel.filterSalaryByPaid(false) },
                    label = { Text(stringResource(Res.string.unpaid)) },
                )
                FilterChip(
                    selected = uiState.salaryPaidFilter == true,
                    onClick = { viewModel.filterSalaryByPaid(true) },
                    label = { Text(stringResource(Res.string.paid)) },
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(payments, key = { it.id }) { payment ->
                    SelectableSalaryPaymentCard(
                        payment = payment,
                        selected = payment.id in uiState.selectedPaymentIds,
                        onToggle = { viewModel.togglePaymentSelection(payment.id) },
                        onMarkUnpaid = { viewModel.markUnpaid(payment) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableSalaryPaymentCard(
    payment: SalaryPayment,
    selected: Boolean,
    onToggle: () -> Unit,
    onMarkUnpaid: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = ""
    )

    Card(
        onClick = { if (!payment.paid) onToggle() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!payment.paid) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() }
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (payment.periodType == "DAY") payment.periodStart else "${payment.periodStart} - ${payment.periodEnd}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${payment.workedDays} days worked",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (payment.overtimeHours > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                Res.string.overtime_included,
                                payment.overtimeHours.toString(),
                                payment.overtimeAmount.toInt().toString()
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (payment.overtimeHours > 0) {
                    // Show base salary
                    Text(
                        text = "${payment.amount.toInt()} ${stringResource(Res.string.egp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show overtime amount
                    Text(
                        text = "+ ${payment.overtimeAmount.toInt()} ${stringResource(Res.string.egp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    HorizontalDivider(modifier = Modifier.width(60.dp).padding(vertical = 2.dp))
                }
                // Show total (or just amount if no overtime)
                Text(
                    text = "${payment.totalAmount.toInt()} ${stringResource(Res.string.egp)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (payment.paid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )

                // Status Indicator
                val statusColor = if (payment.paid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(if (payment.paid) Res.string.paid else Res.string.unpaid),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
        }
    }
}

// ─── Overtime Tab ─────────────────────────────────────────────────

@Composable
private fun OvertimeTab(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    // Two-level navigation: null = worker list, non-null = worker overtime detail
    var selectedWorkerId by remember { mutableStateOf<String?>(null) }

    if (selectedWorkerId == null) {
        OvertimeWorkerListView(uiState, viewModel) { workerId ->
            selectedWorkerId = workerId
            viewModel.refreshOvertimeForWorker(workerId)
        }
    } else {
        OvertimeWorkerDetailView(
            uiState = uiState,
            viewModel = viewModel,
            workerId = selectedWorkerId!!,
            onBack = { selectedWorkerId = null },
        )
    }
}

@Composable
private fun OvertimeWorkerListView(
    uiState: StaffViewModel.UiState,
    viewModel: StaffViewModel,
    onSelectWorker: (String) -> Unit,
) {
    if (uiState.workers.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Timer,
            message = stringResource(Res.string.no_workers),
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.workers, key = { it.id }) { worker ->
                OutlinedCard(
                    onClick = { onSelectWorker(worker.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(
                            photoUrl = worker.photoUrl,
                            size = 44.dp,
                            contentDescription = worker.fullName,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = worker.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        text = worker.role,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun OvertimeWorkerDetailView(
    uiState: StaffViewModel.UiState,
    viewModel: StaffViewModel,
    workerId: String,
    onBack: () -> Unit,
) {
    val worker = uiState.workers.find { it.id == workerId }
    val entries = uiState.overtimeEntries.sortedByDescending { it.date }
    val totalAmount = entries.sumOf { it.amount }
    var showDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                worker?.fullName ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(Res.string.overtime),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddOvertimeDialog(workerId) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(Res.string.add_overtime)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Total summary card
            if (entries.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.overtime),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${totalAmount.toInt()} ${stringResource(Res.string.egp)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "${entries.size} ${stringResource(Res.string.overtime)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Timer,
                    message = stringResource(Res.string.no_overtime_records),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        OvertimeEntryCard(
                            entry = entry,
                            onDelete = { showDeleteId = entry.id },
                            onEditRate = {
                                viewModel.showEditRateDialog(entry.id, entry.ratePerHour)
                            },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteId != null) {
        DeleteConfirmDialog(
            title = stringResource(Res.string.delete_overtime),
            message = stringResource(Res.string.delete_overtime_confirm),
            onConfirm = {
                viewModel.deleteOvertime(showDeleteId!!)
                showDeleteId = null
                viewModel.refreshOvertimeForWorker(workerId)
            },
            onDismiss = { showDeleteId = null },
        )
    }
}

@Composable
private fun OvertimeEntryCard(
    entry: Overtime,
    onDelete: () -> Unit,
    onEditRate: (() -> Unit)? = null,
) {
    val isPendingRate = entry.ratePerHour <= 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPendingRate)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.date,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (isPendingRate) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.pending_rate),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (isPendingRate) {
                        Text(
                            text = stringResource(Res.string.overtime_hours_only, entry.hours.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                Res.string.overtime_entry,
                                entry.hours.toString(),
                                entry.ratePerHour.toInt().toString(),
                                entry.amount.toInt().toString(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (!isPendingRate) {
                        Text(
                            text = "${entry.amount.toInt()} ${stringResource(Res.string.egp)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row {
                        if (onEditRate != null) {
                            IconButton(onClick = onEditRate, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(Res.string.edit_rate),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            val noteText = entry.note
            if (!noteText.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun AddOvertimeDialog(
    uiState: StaffViewModel.UiState,
    viewModel: StaffViewModel,
) {
    val worker = uiState.workers.find { it.id == uiState.overtimeWorkerId }

    AlertDialog(
        onDismissRequest = viewModel::dismissOvertimeDialog,
        icon = { Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.add_overtime))
                if (worker != null) {
                    Text(
                        text = worker.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.overtimeDate,
                    onValueChange = viewModel::updateOvertimeDate,
                    label = { Text(stringResource(Res.string.overtime_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("YYYY-MM-DD") },
                )
                OutlinedTextField(
                    value = uiState.overtimeHours,
                    onValueChange = viewModel::updateOvertimeHours,
                    label = { Text(stringResource(Res.string.overtime_hours)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("e.g. 2.5") },
                )
                OutlinedTextField(
                    value = uiState.overtimeRatePerHour,
                    onValueChange = viewModel::updateOvertimeRatePerHour,
                    label = { Text(stringResource(Res.string.rate_per_hour)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                // Preview calculation
                val hours = uiState.overtimeHours.toDoubleOrNull() ?: 0.0
                val rate = uiState.overtimeRatePerHour.toDoubleOrNull() ?: 0.0
                if (hours > 0 && rate > 0) {
                    val total = hours * rate
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.overtime_entry,
                                hours.toString(),
                                rate.toInt().toString(),
                                total.toInt().toString(),
                            ),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.overtimeNote,
                    onValueChange = viewModel::updateOvertimeNote,
                    label = { Text(stringResource(Res.string.overtime_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::submitOvertime,
                enabled = !uiState.isSaving &&
                    uiState.overtimeDate.isNotBlank() &&
                    (uiState.overtimeHours.toDoubleOrNull() ?: 0.0) > 0,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissOvertimeDialog) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun EditRateDialog(
    uiState: StaffViewModel.UiState,
    viewModel: StaffViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::dismissEditRateDialog,
        icon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.set_rate_per_hour)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.editRateValue,
                    onValueChange = viewModel::updateEditRateValue,
                    label = { Text(stringResource(Res.string.rate_per_hour)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::submitEditRate,
                enabled = !uiState.isSaving &&
                    (uiState.editRateValue.toDoubleOrNull() ?: 0.0) > 0,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissEditRateDialog) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

// ─── Roles Tab ───────────────────────────────────────────────────

@Composable
private fun RolesTab(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Text(
                text = stringResource(Res.string.predefined_roles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (uiState.workerRoles.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Badge,
                    message = stringResource(Res.string.no_roles),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.workerRoles, key = { it.id }) { role ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = role.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (!role.description.isNullOrBlank()) {
                                        Text(
                                            text = role.description ?: stringResource(Res.string.no_job_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.showDeleteRoleConfirm(role) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = viewModel::showAddRoleDialog,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.add_role))
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────

// Step Indicator Component
@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    isMainWorker: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step 1: Basic Info
        StepCircle(
            stepNumber = 1,
            isActive = currentStep == 1,
            isCompleted = currentStep > 1,
            label = stringResource(Res.string.basic_info)
        )
        
        if (currentStep > 1 || isMainWorker) {
            StepConnector(isCompleted = currentStep > 1)
        }
        
        // Step 2: System Access (only for Main Workers)
        if (isMainWorker) {
            StepCircle(
                stepNumber = 2,
                isActive = currentStep == 2,
                isCompleted = currentStep > 2,
                label = stringResource(Res.string.system_access)
            )
            
            if (currentStep > 2 || totalSteps > 2) {
                StepConnector(isCompleted = currentStep > 2)
            }
        }
        
        // Step 3: Salary (always shown, but numbered differently)
        StepCircle(
            stepNumber = if (isMainWorker) 3 else 2,
            isActive = currentStep == 3,
            isCompleted = false,
            label = stringResource(Res.string.salary_configuration)
        )
    }
}

@Composable
private fun StepCircle(
    stepNumber: Int,
    isActive: Boolean,
    isCompleted: Boolean,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepConnector(isCompleted: Boolean) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
            .background(
                if (isCompleted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
    )
}

// Step 1: Basic Info + Worker Type
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step1BasicInfoContent(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Photo picker
        val pickImage = rememberImagePickerLauncher { bytes ->
            if (bytes != null) viewModel.uploadWorkerPhoto(bytes)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box {
                ProfileAvatar(
                    photoUrl = uiState.dialogPhotoUrl,
                    size = 80.dp,
                    contentDescription = "Worker photo",
                )
                Surface(
                    onClick = pickImage,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp).align(Alignment.BottomEnd),
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Change photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.dialogName,
            onValueChange = viewModel::updateDialogName,
            label = { Text(stringResource(Res.string.full_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = uiState.dialogPhone,
            onValueChange = viewModel::updateDialogPhone,
            label = { Text(stringResource(Res.string.phone)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        // PIN Field (Required for all workers)
        OutlinedTextField(
            value = uiState.dialogPin,
            onValueChange = viewModel::updateDialogPin,
            label = { Text(stringResource(Res.string.pin_required)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (uiState.showDialogPin)
                androidx.compose.ui.text.input.VisualTransformation.None
            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowDialogPin) {
                    Icon(
                        if (uiState.showDialogPin) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (uiState.showDialogPin) "Hide PIN" else "Show PIN"
                    )
                }
            },
            supportingText = {
                Text(stringResource(Res.string.pin_length_hint))
            },
            isError = uiState.dialogPin.isNotEmpty() && (uiState.dialogPin.length < 4 || uiState.dialogPin.length > 6)
        )

        // Confirm PIN Field
        OutlinedTextField(
            value = uiState.dialogPinConfirm,
            onValueChange = viewModel::updateDialogPinConfirm,
            label = { Text(stringResource(Res.string.confirm_pin)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (uiState.showDialogPinConfirm)
                androidx.compose.ui.text.input.VisualTransformation.None
            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowDialogPinConfirm) {
                    Icon(
                        if (uiState.showDialogPinConfirm) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (uiState.showDialogPinConfirm) "Hide PIN" else "Show PIN"
                    )
                }
            },
            supportingText = {
                if (uiState.dialogPinConfirm.isNotEmpty() && uiState.dialogPin != uiState.dialogPinConfirm) {
                    Text(
                        stringResource(Res.string.pin_must_match),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            isError = uiState.dialogPinConfirm.isNotEmpty() && uiState.dialogPin != uiState.dialogPinConfirm
        )

        OutlinedTextField(
            value = uiState.dialogDescription,
            onValueChange = viewModel::updateDialogDescription,
            label = { Text(stringResource(Res.string.description_notes)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 2,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Worker Type Selection
        Text(
            stringResource(Res.string.worker_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = uiState.dialogWorkerType == StaffViewModel.WorkerType.NORMAL,
                onClick = { viewModel.updateDialogWorkerType(StaffViewModel.WorkerType.NORMAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.normal_worker))
                    Text(
                        stringResource(Res.string.no_app_access),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }
            SegmentedButton(
                selected = uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN,
                onClick = { viewModel.updateDialogWorkerType(StaffViewModel.WorkerType.MAIN) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.main_worker))
                    Text(
                        stringResource(Res.string.has_app_access),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Role selector - Only show for Normal Workers
        if (uiState.dialogWorkerType == StaffViewModel.WorkerType.NORMAL) {
            var roleExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = roleExpanded,
                onExpandedChange = { roleExpanded = it },
            ) {
                OutlinedTextField(
                    value = uiState.dialogRole,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.select_role)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(
                    expanded = roleExpanded,
                    onDismissRequest = { roleExpanded = false },
                ) {
                    uiState.workerRoles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.name) },
                            onClick = {
                                viewModel.updateDialogRole(role.name)
                                roleExpanded = false
                            },
                        )
                    }
                }
            }
        } else {
            // Show info card for Main Workers
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.main_worker_role_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Step 2: System Access (Main Workers only)
@Composable
private fun Step2SystemAccessContent(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.system_access_setup),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        
        Text(
            stringResource(Res.string.system_access_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        
        Spacer(Modifier.height(4.dp))
        
        // App Role Selection
        Text(
            stringResource(Res.string.app_role),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = uiState.dialogLoginRole == "CASHIER",
                onClick = { viewModel.updateDialogLoginRole("CASHIER") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) {
                Text(stringResource(Res.string.cashier_role))
            }
            SegmentedButton(
                selected = uiState.dialogLoginRole == "DELIVERY",
                onClick = { viewModel.updateDialogLoginRole("DELIVERY") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(Res.string.delivery_role))
            }
            SegmentedButton(
                selected = uiState.dialogLoginRole == "MANAGER",
                onClick = { viewModel.updateDialogLoginRole("MANAGER") },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(Res.string.manager_role))
            }
        }

        OutlinedTextField(
            value = uiState.dialogPassword,
            onValueChange = viewModel::updateDialogPassword,
            label = { Text(stringResource(Res.string.password)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                Text(stringResource(Res.string.password_hint))
            }
        )
        
        // Show selected role info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        Res.string.role_auto_set,
                        when (uiState.dialogLoginRole) {
                            "CASHIER" -> stringResource(Res.string.cashier_role)
                            "DELIVERY" -> stringResource(Res.string.delivery_role)
                            "MANAGER" -> stringResource(Res.string.manager_role)
                            else -> ""
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Step 3: Salary Configuration with Summary
@Composable
private fun Step3SalaryContent(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Salary type
        Text(
            stringResource(Res.string.salary_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.DAILY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.DAILY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) {
                Text(stringResource(Res.string.daily_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.WEEKLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.WEEKLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(Res.string.weekly_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.MONTHLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.MONTHLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(Res.string.monthly_salary))
            }
        }

        val salaryTouched = uiState.dialogSalaryAmount.isNotEmpty()
        val salaryInvalid = salaryTouched && (uiState.dialogSalaryAmount.toDoubleOrNull() ?: 0.0) <= 0

        OutlinedTextField(
            value = uiState.dialogSalaryAmount,
            onValueChange = viewModel::updateDialogSalaryAmount,
            label = { Text(stringResource(Res.string.salary_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            prefix = { Text("${stringResource(Res.string.egp)} ") },
            isError = salaryInvalid || (!salaryTouched && uiState.dialogStep == 3),
            supportingText = {
                if (salaryInvalid) {
                    Text(
                        stringResource(Res.string.salary_required),
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!salaryTouched) {
                    Text(
                        stringResource(Res.string.salary_required),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Summary Card
        Text(
            stringResource(Res.string.summary),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryRow(stringResource(Res.string.full_name), uiState.dialogName)
                SummaryRow(stringResource(Res.string.phone), uiState.dialogPhone.ifBlank { "-" })
                SummaryRow(
                    stringResource(Res.string.worker_type),
                    if (uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN)
                        stringResource(Res.string.main_worker)
                    else stringResource(Res.string.normal_worker)
                )
                if (uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN) {
                    SummaryRow(
                        stringResource(Res.string.app_role),
                        when (uiState.dialogLoginRole) {
                            "CASHIER" -> stringResource(Res.string.cashier_role)
                            "DELIVERY" -> stringResource(Res.string.delivery_role)
                            "MANAGER" -> stringResource(Res.string.manager_role)
                            else -> "-"
                        }
                    )
                }
                SummaryRow(
                    stringResource(Res.string.select_role),
                    uiState.dialogRole.ifBlank { "-" }
                )
                SummaryRow(
                    stringResource(Res.string.salary_type),
                    when (uiState.dialogSalaryType) {
                        SalaryType.DAILY -> stringResource(Res.string.daily_salary)
                        SalaryType.WEEKLY -> stringResource(Res.string.weekly_salary)
                        SalaryType.MONTHLY -> stringResource(Res.string.monthly_salary)
                    }
                )
                SummaryRow(
                    stringResource(Res.string.salary_amount),
                    "${uiState.dialogSalaryAmount.ifBlank { "0" }} ${stringResource(Res.string.egp)}"
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// Edit Worker Content (Single Screen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditWorkerContent(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Photo picker
        val pickImage = rememberImagePickerLauncher { bytes ->
            if (bytes != null) viewModel.uploadWorkerPhoto(bytes)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box {
                ProfileAvatar(
                    photoUrl = uiState.dialogPhotoUrl,
                    size = 80.dp,
                    contentDescription = "Worker photo",
                )
                Surface(
                    onClick = pickImage,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp).align(Alignment.BottomEnd),
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Change photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.dialogName,
            onValueChange = viewModel::updateDialogName,
            label = { Text(stringResource(Res.string.full_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = uiState.dialogPhone,
            onValueChange = viewModel::updateDialogPhone,
            label = { Text(stringResource(Res.string.phone)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        var roleExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = roleExpanded,
            onExpandedChange = { roleExpanded = it },
        ) {
            OutlinedTextField(
                value = uiState.dialogRole,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.select_role)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(
                expanded = roleExpanded,
                onDismissRequest = { roleExpanded = false },
            ) {
                uiState.workerRoles.forEach { role ->
                    DropdownMenuItem(
                        text = { Text(role.name) },
                        onClick = {
                            viewModel.updateDialogRole(role.name)
                            roleExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.dialogDescription,
            onValueChange = viewModel::updateDialogDescription,
            label = { Text(stringResource(Res.string.description_notes)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 2,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // PIN Section for Edit
        Text(
            "Change PIN",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Leave blank to keep current PIN",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.dialogPin,
            onValueChange = viewModel::updateDialogPin,
            label = { Text("New PIN (Optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            supportingText = {
                if (uiState.dialogPin.isNotEmpty() && uiState.dialogPin.length !in 4..6) {
                    Text(
                        "PIN must be 4-6 digits",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            isError = uiState.dialogPin.isNotEmpty() && uiState.dialogPin.length !in 4..6
        )

        if (uiState.dialogPin.isNotEmpty()) {
            OutlinedTextField(
                value = uiState.dialogPinConfirm,
                onValueChange = viewModel::updateDialogPinConfirm,
                label = { Text("Confirm New PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    if (uiState.dialogPinConfirm.isNotEmpty() && uiState.dialogPin != uiState.dialogPinConfirm) {
                        Text(
                            "PINs do not match",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                isError = uiState.dialogPinConfirm.isNotEmpty() && uiState.dialogPin != uiState.dialogPinConfirm
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            stringResource(Res.string.salary_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.DAILY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.DAILY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) {
                Text(stringResource(Res.string.daily_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.WEEKLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.WEEKLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(Res.string.weekly_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.MONTHLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.MONTHLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(Res.string.monthly_salary))
            }
        }

        val editSalaryTouched = uiState.dialogSalaryAmount.isNotEmpty()
        val editSalaryInvalid = editSalaryTouched && (uiState.dialogSalaryAmount.toDoubleOrNull() ?: 0.0) <= 0

        OutlinedTextField(
            value = uiState.dialogSalaryAmount,
            onValueChange = viewModel::updateDialogSalaryAmount,
            label = { Text(stringResource(Res.string.salary_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            prefix = { Text("${stringResource(Res.string.egp)} ") },
            isError = editSalaryInvalid || !editSalaryTouched,
            supportingText = {
                if (editSalaryInvalid || !editSalaryTouched) {
                    Text(
                        stringResource(Res.string.salary_required),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditWorkerBottomSheet(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    val isEdit = uiState.editingWorker != null
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Validation helpers
    fun isStep1Valid(): Boolean {
        val pinValid = uiState.dialogPin.length in 4..6 &&
                uiState.dialogPin == uiState.dialogPinConfirm
        return uiState.dialogName.isNotBlank() &&
                pinValid &&
               (isEdit || uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN || uiState.dialogRole.isNotBlank())
    }

    fun isStep2Valid(): Boolean {
        return uiState.dialogPassword.length >= 6
    }

    fun isStep3Valid(): Boolean {
        val salary = uiState.dialogSalaryAmount.toDoubleOrNull()
        return salary != null && salary > 0
    }

    fun isFormValid(): Boolean {
        // For edit mode: PIN is optional, but if provided must be valid
        val pinValid = if (isEdit) {
            uiState.dialogPin.isEmpty() ||
                    (uiState.dialogPin.length in 4..6 && uiState.dialogPin == uiState.dialogPinConfirm)
        } else {
            uiState.dialogPin.length in 4..6 && uiState.dialogPin == uiState.dialogPinConfirm
        }

        // For edit mode: password is NOT required (keep existing password)
        val passwordValid = if (isEdit) {
            true
        } else {
            !uiState.dialogIsLoginEnabled || uiState.dialogPassword.length >= 6
        }

        // Salary is mandatory
        val salaryValid = (uiState.dialogSalaryAmount.toDoubleOrNull() ?: 0.0) > 0

        return uiState.dialogName.isNotBlank() &&
               uiState.dialogRole.isNotBlank() &&
                pinValid &&
                passwordValid &&
                salaryValid
    }

    val totalSteps = if (uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN) 3 else 2

    ModalBottomSheet(
        onDismissRequest = viewModel::dismissWorkerDialog,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ─── Header ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isEdit) Icons.Filled.Edit else Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isEdit) stringResource(Res.string.edit_worker) else stringResource(Res.string.add_worker),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!isEdit) {
                            Text(
                                text = stringResource(Res.string.step_of, uiState.dialogStep, totalSteps),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Step indicator for multi-step flow (only for new workers)
                if (!isEdit) {
                    Spacer(Modifier.height(16.dp))
                    StepIndicator(
                        currentStep = uiState.dialogStep,
                        totalSteps = totalSteps,
                        isMainWorker = uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // ─── Scrollable Content ────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isEdit) {
                    // Edit mode: all fields in single view with section cards
                    SheetSectionCard(
                        icon = Icons.Filled.Person,
                        title = stringResource(Res.string.basic_information),
                    ) {
                        EditWorkerContent(uiState, viewModel)
                    }
                } else {
                    when (uiState.dialogStep) {
                        1 -> {
                            SheetSectionCard(
                                icon = Icons.Filled.Person,
                                title = stringResource(Res.string.basic_information),
                            ) {
                                Step1BasicInfoContent(uiState, viewModel)
                            }
                        }
                        2 -> {
                            SheetSectionCard(
                                icon = Icons.Filled.Lock,
                                title = stringResource(Res.string.system_access_section),
                            ) {
                                Step2SystemAccessContent(uiState, viewModel)
                            }
                        }
                        3 -> {
                            SheetSectionCard(
                                icon = Icons.Filled.AttachMoney,
                                title = stringResource(Res.string.salary_payment_section),
                            ) {
                                Step3SalaryContent(uiState, viewModel)
                            }
                        }
                    }
                }
            }

            // ─── Sticky Bottom Action Bar ────────────────────────
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isEdit) {
                        // Edit mode: Cancel + Save
                        TextButton(
                            onClick = viewModel::dismissWorkerDialog,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(Res.string.cancel))
                        }
                        Button(
                            onClick = viewModel::saveWorker,
                            enabled = !uiState.isSaving && isFormValid(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (uiState.isSaving) stringResource(Res.string.saving)
                                else stringResource(Res.string.save)
                            )
                        }
                    } else {
                        // Multi-step: Back/Cancel + Next/Create
                        if (uiState.dialogStep > 1) {
                            TextButton(
                                onClick = viewModel::previousDialogStep,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(Res.string.back))
                            }
                        } else {
                            TextButton(
                                onClick = viewModel::dismissWorkerDialog,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(Res.string.cancel))
                            }
                        }

                        val isLastStep = uiState.dialogStep == 3
                        Button(
                            onClick = {
                                if (isLastStep) viewModel.saveWorker()
                                else viewModel.nextDialogStep()
                            },
                            enabled = when (uiState.dialogStep) {
                                1 -> isStep1Valid()
                                2 -> isStep2Valid()
                                3 -> !uiState.isSaving && isStep3Valid() && isFormValid()
                                else -> false
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                when {
                                    uiState.isSaving -> stringResource(Res.string.saving)
                                    isLastStep -> stringResource(Res.string.create_worker)
                                    else -> stringResource(Res.string.next)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Section card wrapper for bottom sheet content
@Composable
private fun SheetSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}
@Composable
private fun AddRoleDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissRoleDialog,
        icon = { Icon(Icons.Filled.Badge, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.add_role)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.dialogRoleName,
                    onValueChange = viewModel::updateDialogRoleName,
                    label = { Text(stringResource(Res.string.role_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = uiState.dialogRoleDescription,
                    onValueChange = viewModel::updateDialogRoleDescription,
                    label = { Text(stringResource(Res.string.role_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::saveRole,
                enabled = !uiState.isSaving && uiState.dialogRoleName.isNotBlank(),
            ) {
                Text(if (uiState.isSaving) stringResource(Res.string.saving) else stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissRoleDialog) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun BatchPayNoteDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissBatchPayDialog,
        title = { Text(stringResource(Res.string.pay_selected, uiState.selectedPaymentIds.size)) },
        text = {
            OutlinedTextField(
                value = uiState.batchPayNote,
                onValueChange = viewModel::updateBatchPayNote,
                label = { Text(stringResource(Res.string.payment_note)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
            )
        },
        confirmButton = {
            Button(
                onClick = viewModel::batchPay,
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.isSaving) stringResource(Res.string.saving) else stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissBatchPayDialog) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
