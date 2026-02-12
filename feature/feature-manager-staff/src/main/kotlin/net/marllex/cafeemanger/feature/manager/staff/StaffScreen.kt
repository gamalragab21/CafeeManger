package net.marllex.cafeemanger.feature.manager.staff

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.model.Attendance
import net.marllex.cafeemanger.core.model.SalaryPayment
import net.marllex.cafeemanger.core.model.SalaryType
import net.marllex.cafeemanger.core.model.Worker
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    viewModel: StaffViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    // Sub-screen state: null = main tabs, "delivery" or "announcements"
    var activeSubScreen by remember { mutableStateOf<String?>(null) }
    val tabs = listOf(
        stringResource(R.string.workers),
        stringResource(R.string.attendance),
        stringResource(R.string.salary),
        stringResource(R.string.roles_settings),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (activeSubScreen) {
                            "delivery" -> stringResource(R.string.delivery_dashboard)
                            "announcements" -> stringResource(R.string.announcements)
                            else -> stringResource(R.string.staff_management)
                        }
                    )
                },
                navigationIcon = {
                    if (activeSubScreen != null) {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
                                        stringResource(R.string.delivery_dashboard),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Text(
                                        stringResource(R.string.view_delivery_dashboard),
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
                                        stringResource(R.string.announcements),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Text(
                                        stringResource(R.string.view_announcements),
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
                                0 -> WorkersTab(uiState, viewModel)
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
                title = stringResource(R.string.delete_worker),
                message = stringResource(R.string.delete_worker_confirm),
                onConfirm = viewModel::confirmDeleteWorker,
                onDismiss = viewModel::dismissDeleteWorkerDialog,
            )
        }
        if (uiState.showAddRoleDialog) {
            AddRoleDialog(uiState, viewModel)
        }
        if (uiState.showDeleteRoleDialog) {
            DeleteConfirmDialog(
                title = stringResource(R.string.delete_role),
                message = stringResource(R.string.delete_role_confirm),
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
private fun WorkersTab(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
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
                message = stringResource(R.string.no_workers),
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
                            label = { Text(stringResource(R.string.all)) },
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
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_worker))
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = if (!worker.active) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) else CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Presence indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPresent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = worker.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    worker.workerId,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(24.dp),
                        )
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    worker.role,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(24.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                        if (worker.isLoginEnabled) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        stringResource(R.string.can_login),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(24.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                            )
                        }
                    }
                }

                if (!worker.active) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.inactive),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = when (worker.salaryType) {
                            SalaryType.DAILY -> stringResource(R.string.daily_salary)
                            SalaryType.WEEKLY -> stringResource(R.string.weekly_salary)
                            SalaryType.MONTHLY -> stringResource(R.string.monthly_salary)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${
                            worker.salaryAmount.toBigDecimal().toPlainString()
                        } ${stringResource(R.string.egp)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            if (worker.active) Icons.Filled.PersonOff else Icons.Filled.PersonAdd,
                            if (worker.active) "Deactivate" else "Activate",
                            tint = if (worker.active) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
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
                        text = stringResource(R.string.who_is_working),
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
                            label = stringResource(R.string.present),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        AttendanceCountChip(
                            count = absentCount,
                            label = stringResource(R.string.absent),
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
                                if (summary.presentToday) stringResource(R.string.present)
                                else stringResource(R.string.absent),
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
                text = stringResource(R.string.attendance_history),
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
                    message = stringResource(R.string.no_attendance_records),
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
                value = workers.find { it.id == selectedWorkerId }?.fullName ?: stringResource(R.string.all_workers),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.filter_by_worker)) },
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
                    text = { Text(stringResource(R.string.all_workers)) },
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
                label = { Text(stringResource(R.string.from_date)) },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = toDate,
                onValueChange = onToDateChanged,
                label = { Text(stringResource(R.string.to_date)) },
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
                label = { Text(stringResource(R.string.all)) },
            )
            FilterChip(
                selected = statusFilter == "PRESENT",
                onClick = { onStatusFilterChanged(if (statusFilter == "PRESENT") null else "PRESENT") },
                label = { Text(stringResource(R.string.present)) },
            )
            FilterChip(
                selected = statusFilter == "ABSENT",
                onClick = { onStatusFilterChanged(if (statusFilter == "ABSENT") null else "ABSENT") },
                label = { Text(stringResource(R.string.absent)) },
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
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

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
                    text = record.workerName ?: stringResource(R.string.worker),
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
                        R.string.check_in_time,
                        timeFormatter.format(Date(record.checkIn))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                record.checkOut?.let {
                    Text(
                        text = stringResource(
                            R.string.check_out_time,
                            timeFormatter.format(Date(it))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                record.workedMinutes?.let {

                    Text(
                        text = stringResource(R.string.worked, record.workedHoursFormatted),
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

    Column {
        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = uiState.salaryPaidFilter == null,
                    onClick = { viewModel.filterSalaryByPaid(null) },
                    label = { Text(stringResource(R.string.all)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
            item {
                FilterChip(
                    selected = uiState.salaryPaidFilter == false,
                    onClick = { viewModel.filterSalaryByPaid(false) },
                    label = { Text(stringResource(R.string.unpaid)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                )
            }
            item {
                FilterChip(
                    selected = uiState.salaryPaidFilter == true,
                    onClick = { viewModel.filterSalaryByPaid(true) },
                    label = { Text(stringResource(R.string.paid)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }

        if (workerSummaries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Payments,
                message = stringResource(R.string.no_salary_records),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(workerSummaries, key = { it.workerId }) { summary ->
                    Card(
                        onClick = { viewModel.selectWorkerForSalary(summary.workerId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = summary.workerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${summary.workerRole} \u2022 ${summary.salaryType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (summary.unpaidCount > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = stringResource(R.string.unpaid_balance),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${summary.unpaidAmount.toBigDecimal().toPlainString()} ${stringResource(R.string.egp)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            text = stringResource(R.string.unpaid_records_count, summary.unpaidCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerSalaryDetailView(
    uiState: StaffViewModel.UiState,
    viewModel: StaffViewModel,
    workerId: String,
) {
    val worker = uiState.workers.find { it.id == workerId }
    val payments = remember(uiState.salaryPayments, uiState.salaryPaidFilter, workerId) {
        val filtered = uiState.salaryPayments.filter { it.workerId == workerId }
        when (uiState.salaryPaidFilter) {
            true -> filtered.filter { it.paid }
            false -> filtered.filter { !it.paid }
            null -> filtered
        }.sortedByDescending { it.periodStart }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.selectWorkerForSalary(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = worker?.fullName ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${worker?.role ?: ""} \u2022 ${worker?.salaryType?.name ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == null,
                        onClick = { viewModel.filterSalaryByPaid(null) },
                        label = { Text(stringResource(R.string.all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == false,
                        onClick = { viewModel.filterSalaryByPaid(false) },
                        label = { Text(stringResource(R.string.unpaid)) },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == true,
                        onClick = { viewModel.filterSalaryByPaid(true) },
                        label = { Text(stringResource(R.string.paid)) },
                    )
                }
            }

            if (payments.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Payments,
                    message = stringResource(R.string.no_salary_records),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(payments, key = { it.id }) { payment ->
                        SelectableSalaryPaymentCard(
                            payment = payment,
                            selected = payment.id in uiState.selectedPaymentIds,
                            onToggle = { viewModel.togglePaymentSelection(payment.id) },
                            onMarkUnpaid = { viewModel.markUnpaid(payment) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB: Pay Selected
        if (uiState.selectedPaymentIds.isNotEmpty()) {
            FloatingActionButton(
                onClick = viewModel::showBatchPayDialog,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Text(stringResource(R.string.pay_selected, uiState.selectedPaymentIds.size))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = if (selected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ) else CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Checkbox for unpaid items
            if (!payment.paid) {
                androidx.compose.material3.Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (payment.periodType == "DAY") payment.periodStart
                    else "${payment.periodStart} - ${payment.periodEnd}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${payment.periodType} \u2022 ${stringResource(R.string.worked_days, payment.workedDays)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${payment.amount.toBigDecimal().toPlainString()} ${stringResource(R.string.egp)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (payment.paid) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                AssistChip(
                    onClick = { if (payment.paid) onMarkUnpaid() },
                    label = {
                        Text(
                            if (payment.paid) stringResource(R.string.paid) else stringResource(R.string.unpaid),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.height(24.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (payment.paid)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ),
                )
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
                text = stringResource(R.string.predefined_roles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (uiState.workerRoles.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Badge,
                    message = stringResource(R.string.no_roles),
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
                                            text = role.description ?: stringResource(R.string.no_job_description),
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
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_role))
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
            label = stringResource(R.string.basic_info)
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
                label = stringResource(R.string.system_access)
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
            label = stringResource(R.string.salary_configuration)
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
            label = { Text(stringResource(R.string.full_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = uiState.dialogPhone,
            onValueChange = viewModel::updateDialogPhone,
            label = { Text(stringResource(R.string.phone)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = uiState.dialogDescription,
            onValueChange = viewModel::updateDialogDescription,
            label = { Text(stringResource(R.string.description_notes)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 2,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Worker Type Selection
        Text(
            stringResource(R.string.worker_type),
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
                    Text(stringResource(R.string.normal_worker))
                    Text(
                        stringResource(R.string.no_app_access),
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
                    Text(stringResource(R.string.main_worker))
                    Text(
                        stringResource(R.string.has_app_access),
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
                    label = { Text(stringResource(R.string.select_role)) },
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
                        text = stringResource(R.string.main_worker_role_info),
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
            stringResource(R.string.system_access_setup),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        
        Text(
            stringResource(R.string.system_access_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        
        Spacer(Modifier.height(4.dp))
        
        // App Role Selection
        Text(
            stringResource(R.string.app_role),
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
                Text(stringResource(R.string.cashier_role))
            }
            SegmentedButton(
                selected = uiState.dialogLoginRole == "DELIVERY",
                onClick = { viewModel.updateDialogLoginRole("DELIVERY") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(R.string.delivery_role))
            }
            SegmentedButton(
                selected = uiState.dialogLoginRole == "MANAGER",
                onClick = { viewModel.updateDialogLoginRole("MANAGER") },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(R.string.manager_role))
            }
        }

        OutlinedTextField(
            value = uiState.dialogPassword,
            onValueChange = viewModel::updateDialogPassword,
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                Text(stringResource(R.string.password_hint))
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
                        R.string.role_auto_set,
                        when (uiState.dialogLoginRole) {
                            "CASHIER" -> stringResource(R.string.cashier_role)
                            "DELIVERY" -> stringResource(R.string.delivery_role)
                            "MANAGER" -> stringResource(R.string.manager_role)
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
            stringResource(R.string.salary_type),
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
                Text(stringResource(R.string.daily_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.WEEKLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.WEEKLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(R.string.weekly_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.MONTHLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.MONTHLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(R.string.monthly_salary))
            }
        }

        OutlinedTextField(
            value = uiState.dialogSalaryAmount,
            onValueChange = viewModel::updateDialogSalaryAmount,
            label = { Text(stringResource(R.string.salary_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            prefix = { Text("${stringResource(R.string.egp)} ") }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Summary Card
        Text(
            stringResource(R.string.summary),
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
                SummaryRow(stringResource(R.string.full_name), uiState.dialogName)
                SummaryRow(stringResource(R.string.phone), uiState.dialogPhone.ifBlank { "-" })
                SummaryRow(
                    stringResource(R.string.worker_type),
                    if (uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN)
                        stringResource(R.string.main_worker)
                    else stringResource(R.string.normal_worker)
                )
                if (uiState.dialogWorkerType == StaffViewModel.WorkerType.MAIN) {
                    SummaryRow(
                        stringResource(R.string.app_role),
                        when (uiState.dialogLoginRole) {
                            "CASHIER" -> stringResource(R.string.cashier_role)
                            "DELIVERY" -> stringResource(R.string.delivery_role)
                            "MANAGER" -> stringResource(R.string.manager_role)
                            else -> "-"
                        }
                    )
                }
                SummaryRow(
                    stringResource(R.string.select_role),
                    uiState.dialogRole.ifBlank { "-" }
                )
                SummaryRow(
                    stringResource(R.string.salary_type),
                    when (uiState.dialogSalaryType) {
                        SalaryType.DAILY -> stringResource(R.string.daily_salary)
                        SalaryType.WEEKLY -> stringResource(R.string.weekly_salary)
                        SalaryType.MONTHLY -> stringResource(R.string.monthly_salary)
                    }
                )
                SummaryRow(
                    stringResource(R.string.salary_amount),
                    "${uiState.dialogSalaryAmount.ifBlank { "0" }} ${stringResource(R.string.egp)}"
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
            label = { Text(stringResource(R.string.full_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = uiState.dialogPhone,
            onValueChange = viewModel::updateDialogPhone,
            label = { Text(stringResource(R.string.phone)) },
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
                label = { Text(stringResource(R.string.select_role)) },
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
            label = { Text(stringResource(R.string.description_notes)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 2,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            stringResource(R.string.salary_type),
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
                Text(stringResource(R.string.daily_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.WEEKLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.WEEKLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(R.string.weekly_salary))
            }
            SegmentedButton(
                selected = uiState.dialogSalaryType == SalaryType.MONTHLY,
                onClick = { viewModel.updateDialogSalaryType(SalaryType.MONTHLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(R.string.monthly_salary))
            }
        }

        OutlinedTextField(
            value = uiState.dialogSalaryAmount,
            onValueChange = viewModel::updateDialogSalaryAmount,
            label = { Text(stringResource(R.string.salary_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            prefix = { Text("${stringResource(R.string.egp)} ") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditWorkerDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    val isEdit = uiState.editingWorker != null
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    // Validation helpers
    fun isStep1Valid(): Boolean {
        return uiState.dialogName.isNotBlank() && 
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
        return uiState.dialogName.isNotBlank() && 
               uiState.dialogRole.isNotBlank() &&
               (!uiState.dialogIsLoginEnabled || uiState.dialogPassword.length >= 6)
    }

    // Determine dialog title based on step
    val dialogTitle = when {
        isEdit -> stringResource(R.string.edit_worker)
        uiState.dialogStep == 1 -> stringResource(R.string.add_worker_step_1)
        uiState.dialogStep == 2 -> stringResource(R.string.add_worker_step_2)
        else -> stringResource(R.string.add_worker_step_3)
    }

    AlertDialog(
        onDismissRequest = viewModel::dismissWorkerDialog,
        modifier = Modifier.widthIn(max = if (screenWidth > 600) 520.dp else 560.dp),
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
                    Text(if (uiState.isSaving) stringResource(R.string.saving) else stringResource(R.string.save))
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
                            uiState.isSaving -> stringResource(R.string.saving)
                            isLastStep -> stringResource(R.string.create_worker)
                            else -> stringResource(R.string.next)
                        }
                    )
                }
            }
        },
        dismissButton = {
            if (!isEdit && uiState.dialogStep > 1) {
                // Show Back button for multi-step flow
                TextButton(onClick = viewModel::previousDialogStep) {
                    Text(stringResource(R.string.back))
                }
            } else {
                // Show Cancel button
                TextButton(onClick = viewModel::dismissWorkerDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
@Composable
private fun AddRoleDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissRoleDialog,
        icon = { Icon(Icons.Filled.Badge, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.add_role)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.dialogRoleName,
                    onValueChange = viewModel::updateDialogRoleName,
                    label = { Text(stringResource(R.string.role_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = uiState.dialogRoleDescription,
                    onValueChange = viewModel::updateDialogRoleDescription,
                    label = { Text(stringResource(R.string.role_description)) },
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
                Text(if (uiState.isSaving) stringResource(R.string.saving) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissRoleDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun BatchPayNoteDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissBatchPayDialog,
        title = { Text(stringResource(R.string.pay_selected, uiState.selectedPaymentIds.size)) },
        text = {
            OutlinedTextField(
                value = uiState.batchPayNote,
                onValueChange = viewModel::updateBatchPayNote,
                label = { Text(stringResource(R.string.payment_note)) },
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
                Text(if (uiState.isSaving) stringResource(R.string.saving) else stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissBatchPayDialog) {
                Text(stringResource(R.string.cancel))
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
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
