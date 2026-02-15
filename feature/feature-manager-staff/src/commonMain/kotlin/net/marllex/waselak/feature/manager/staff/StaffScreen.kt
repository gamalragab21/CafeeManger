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
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import net.marllex.waselak.core.model.SalaryPayment
import net.marllex.waselak.core.model.SalaryType
import net.marllex.waselak.core.model.Worker
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.koin.compose.viewmodel.koinViewModel
import net.marllex.waselak.core.common.extensions.formatEpochMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    onNavigateToWorkerQrCode: (String) -> Unit = {},
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

                    // 4-tab TabRow
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
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
                                1 -> AttendanceTab(uiState, viewModel)
                                2 -> SalaryTab(uiState, viewModel)
                                3 -> RolesTab(uiState, viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Dialogs
        if (uiState.showAddWorkerDialog) {
            AddEditWorkerDialog(uiState, viewModel)
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                worker.fullName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    // Presence Dot
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPresent) Color(0xFF4CAF50) // Success Green
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

@Composable
private fun AttendanceTab(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    val presentCount = uiState.todaySummary.count { it.presentToday }
    val absentCount = uiState.todaySummary.count { !it.presentToday }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Today's Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(Res.string.who_is_working),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        AttendanceCountChip(
                            count = presentCount,
                            label = stringResource(Res.string.present),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        AttendanceCountChip(
                            count = absentCount,
                            label = stringResource(Res.string.absent),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // Worker attendance status list
        items(uiState.todaySummary) { summary ->
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (summary.presentToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.workerName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = summary.workerRole,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (summary.presentToday) stringResource(Res.string.present)
                                else stringResource(Res.string.absent),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.height(28.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (summary.presentToday)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                            labelColor = if (summary.presentToday)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }
        }

        // Attendance History with Filters
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.attendance_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // Filter section
        item {
            AttendanceFilters(
                workers = uiState.workers,
                selectedWorkerId = uiState.attendanceWorkerFilter,
                fromDate = uiState.attendanceFromDate,
                toDate = uiState.attendanceToDate,
                statusFilter = uiState.attendanceStatusFilter,
                onWorkerSelected = viewModel::setAttendanceWorkerFilter,
                onFromDateChanged = viewModel::setAttendanceFromDate,
                onToDateChanged = viewModel::setAttendanceToDate,
                onStatusFilterChanged = viewModel::setAttendanceStatusFilter,
            )
        }

        val filteredRecords = uiState.attendanceRecords.let { records ->
            when (uiState.attendanceStatusFilter) {
                "PRESENT" -> records.filter { it.checkIn > 0 }
                "ABSENT" -> records.filter { it.checkIn <= 0 }
                else -> records
            }
        }

        if (filteredRecords.isNotEmpty()) {
            items(filteredRecords) { record ->
                AttendanceRecordCard(record)
            }
        }

        if (uiState.todaySummary.isEmpty() && filteredRecords.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.EventBusy,
                    message = stringResource(Res.string.no_attendance_records),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceFilters(
    workers: List<Worker>,
    selectedWorkerId: String?,
    fromDate: String,
    toDate: String,
    statusFilter: String?,
    onWorkerSelected: (String?) -> Unit,
    onFromDateChanged: (String) -> Unit,
    onToDateChanged: (String) -> Unit,
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
                        text = { Text(worker.fullName) },
                        onClick = {
                            onWorkerSelected(worker.id)
                            workerExpanded = false
                        },
                    )
                }
            }
        }

        // Date range filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = fromDate,
                onValueChange = onFromDateChanged,
                label = { Text(stringResource(Res.string.from_date)) },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = toDate,
                onValueChange = onToDateChanged,
                label = { Text(stringResource(Res.string.to_date)) },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        // Status filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { onStatusFilterChanged(null) },
                label = { Text(stringResource(Res.string.all)) },
            )
            FilterChip(
                selected = statusFilter == "PRESENT",
                onClick = { onStatusFilterChanged(if (statusFilter == "PRESENT") null else "PRESENT") },
                label = { Text(stringResource(Res.string.present)) },
            )
            FilterChip(
                selected = statusFilter == "ABSENT",
                onClick = { onStatusFilterChanged(if (statusFilter == "ABSENT") null else "ABSENT") },
                label = { Text(stringResource(Res.string.absent)) },
            )
        }
    }
}

@Composable
private fun AttendanceCountChip(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun AttendanceRecordCard(record: Attendance) {

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.workerName ?: stringResource(Res.string.worker),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = record.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        Res.string.check_in_time,
                        record.checkIn.formatEpochMs("hh:mm a")
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                record.checkOut?.let {
                    Text(
                        text = stringResource(
                            Res.string.check_out_time,
                            it.formatEpochMs("hh:mm a")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                record.workedMinutes?.let {

                    Text(
                        text = stringResource(Res.string.worked, record.workedHoursFormatted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                workerRole = worker.role,
                salaryType = worker.salaryType.name,
                unpaidAmount = unpaid.sumOf { it.amount },
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ... (Filter Chips - Use ElevatedFilterChip for better UX)
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == null,
                        onClick = { viewModel.filterSalaryByPaid(null) },
                        label = { Text(stringResource(Res.string.all)) },
                        leadingIcon = { if(uiState.salaryPaidFilter == null) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    )
                }
                // Repeat for Unpaid (use error colors) and Paid (use primary)
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
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == null,
                        onClick = { viewModel.filterSalaryByPaid(null) },
                        label = { Text(stringResource(Res.string.all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == false,
                        onClick = { viewModel.filterSalaryByPaid(false) },
                        label = { Text(stringResource(Res.string.unpaid)) },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == true,
                        onClick = { viewModel.filterSalaryByPaid(true) },
                        label = { Text(stringResource(Res.string.paid)) },
                    )
                }
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
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${payment.amount.toInt()} EGP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (payment.paid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )

                // Status Indicator
                val statusColor = if (payment.paid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (payment.paid) "Paid" else "Unpaid",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
        }
    }
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

        OutlinedTextField(
            value = uiState.dialogSalaryAmount,
            onValueChange = viewModel::updateDialogSalaryAmount,
            label = { Text(stringResource(Res.string.salary_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            prefix = { Text("${stringResource(Res.string.egp)} ") }
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

        OutlinedTextField(
            value = uiState.dialogSalaryAmount,
            onValueChange = viewModel::updateDialogSalaryAmount,
            label = { Text(stringResource(Res.string.salary_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            prefix = { Text("${stringResource(Res.string.egp)} ") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditWorkerDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    val isEdit = uiState.editingWorker != null
    val scrollState = rememberScrollState()

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
        return uiState.dialogSalaryAmount.toDoubleOrNull() != null && 
               uiState.dialogSalaryAmount.toDoubleOrNull()!! > 0
    }
    
    fun isFormValid(): Boolean {
        // For edit mode: PIN is optional, but if provided must be valid
        val pinValid = if (isEdit) {
            uiState.dialogPin.isEmpty() ||
                    (uiState.dialogPin.length in 4..6 && uiState.dialogPin == uiState.dialogPinConfirm)
        } else {
            uiState.dialogPin.length in 4..6 && uiState.dialogPin == uiState.dialogPinConfirm
        }
        
        return uiState.dialogName.isNotBlank() && 
               uiState.dialogRole.isNotBlank() &&
                pinValid &&
               (!uiState.dialogIsLoginEnabled || uiState.dialogPassword.length >= 6)
    }

    // Determine dialog title based on step
    val dialogTitle = when {
        isEdit -> stringResource(Res.string.edit_worker)
        uiState.dialogStep == 1 -> stringResource(Res.string.add_worker_step_1)
        uiState.dialogStep == 2 -> stringResource(Res.string.add_worker_step_2)
        else -> stringResource(Res.string.add_worker_step_3)
    }

    BoxWithConstraints {
    val screenWidth = maxWidth

    AlertDialog(
        onDismissRequest = viewModel::dismissWorkerDialog,
        modifier = Modifier.widthIn(max = if (screenWidth > 600.dp) 520.dp else 560.dp),
        icon = {
            Icon(
                if (isEdit) Icons.Filled.Edit else Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Column {
                Text(
                    dialogTitle,
                    style = MaterialTheme.typography.headlineSmall
                )
                // Step indicator for multi-step flow (only for new workers)
                if (!isEdit) {
                    Spacer(Modifier.height(8.dp))
                    StepIndicator(
                        currentStep = uiState.dialogStep,
                        totalSteps = if (uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN) 3 else 2,
                        isMainWorker = uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth()
            ) {
                // Edit mode: Show all fields in single screen
                if (isEdit) {
                    EditWorkerContent(uiState, viewModel)
                } else {
                    // Multi-step flow for new workers
                    when (uiState.dialogStep) {
                        1 -> Step1BasicInfoContent(uiState, viewModel)
                        2 -> Step2SystemAccessContent(uiState, viewModel)
                        3 -> Step3SalaryContent(uiState, viewModel)
                    }
                }
            }
        },
        confirmButton = {
            if (isEdit) {
                // Edit mode: Single Save button
                Button(
                    onClick = viewModel::saveWorker,
                    enabled = !uiState.isSaving && isFormValid(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (uiState.isSaving) stringResource(Res.string.saving) else stringResource(Res.string.save))
                }
            } else {
                // Multi-step mode: Next/Save button
                val isLastStep = (uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN && uiState.dialogStep == 3) ||
                                 (uiState.dialogWorkerType == StaffViewModel.WorkerType.NORMAL && uiState.dialogStep == 3)
                
                Button(
                    onClick = {
                        if (isLastStep) {
                            viewModel.saveWorker()
                        } else {
                            viewModel.nextDialogStep()
                        }
                    },
                    enabled = when (uiState.dialogStep) {
                        1 -> isStep1Valid()
                        2 -> isStep2Valid()
                        3 -> !uiState.isSaving && isFormValid()
                        else -> false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
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
        },
        dismissButton = {
            if (!isEdit && uiState.dialogStep > 1) {
                // Show Back button for multi-step flow
                TextButton(onClick = viewModel::previousDialogStep) {
                    Text(stringResource(Res.string.back))
                }
            } else {
                // Show Cancel button
                TextButton(onClick = viewModel::dismissWorkerDialog) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
    } // BoxWithConstraints
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
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
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
