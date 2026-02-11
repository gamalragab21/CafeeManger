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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Warning
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
    val tabs = listOf(
        stringResource(R.string.workers),
        stringResource(R.string.attendance),
        stringResource(R.string.salary),
        stringResource(R.string.roles_settings),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.staff_management)) },
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
                }
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
                        }
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
        if (uiState.showSalaryDialog) {
            CalculateSalaryDialog(uiState, viewModel)
        }
        if (uiState.showPayNoteDialog) {
            PayNoteDialog(uiState, viewModel)
        }
        if (uiState.showGenerateDialog) {
            GenerateSalariesDialog(uiState, viewModel)
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
                        text = if (worker.salaryType == SalaryType.DAILY)
                            stringResource(R.string.daily_salary) else stringResource(R.string.monthly_salary),
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
    val filteredPayments = remember(uiState.salaryPayments, uiState.salaryPaidFilter) {
        if (uiState.salaryPaidFilter == null) uiState.salaryPayments
        else uiState.salaryPayments.filter { it.paid == uiState.salaryPaidFilter }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        selected = uiState.salaryPaidFilter == true,
                        onClick = { viewModel.filterSalaryByPaid(true) },
                        label = { Text(stringResource(R.string.paid)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.salaryPaidFilter == false,
                        onClick = { viewModel.filterSalaryByPaid(false) },
                        label = { Text(stringResource(R.string.not_paid)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    )
                }
            }

            if (filteredPayments.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Payments,
                    message = stringResource(R.string.no_salary_records),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredPayments, key = { it.id }) { payment ->
                        SalaryPaymentCard(
                            payment = payment,
                            onMarkPaid = { viewModel.showPayNoteDialog(payment) },
                            onMarkUnpaid = { viewModel.markUnpaid(payment) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingActionButton(
                onClick = viewModel::showGenerateDialog,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.generate_salaries),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            FloatingActionButton(
                onClick = viewModel::showSalaryDialog,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    Icons.Filled.Calculate,
                    contentDescription = stringResource(R.string.calculate_salary)
                )
            }
        }
    }
}

@Composable
private fun SalaryPaymentCard(
    payment: SalaryPayment,
    onMarkPaid: () -> Unit,
    onMarkUnpaid: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payment.workerName ?: stringResource(R.string.worker),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${payment.periodStart} - ${payment.periodEnd}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (payment.paid) stringResource(R.string.paid) else stringResource(R.string.not_paid),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.height(28.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (payment.paid)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        labelColor = if (payment.paid)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.worked_days, payment.workedDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${
                        payment.amount.toBigDecimal().toPlainString()
                    } ${stringResource(R.string.egp)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!payment.paid) {
                Button(
                    onClick = onMarkPaid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mark_as_paid))
                }
            } else {
                OutlinedButton(
                    onClick = onMarkUnpaid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.mark_as_unpaid))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditWorkerDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    val isEdit = uiState.editingWorker != null
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    AlertDialog(
        onDismissRequest = viewModel::dismissWorkerDialog,
        modifier = Modifier
            // Limit width on tablets so it doesn't stretch to the edges
            .widthIn(max = if (screenWidth > 600) 480.dp else 560.dp),
        icon = {
            Icon(
                if (isEdit) Icons.Filled.Edit else Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                if (isEdit) stringResource(R.string.edit_worker)
                else stringResource(R.string.add_worker),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    // 1. Crucial for adaptive screens: allow scrolling when keyboard is up
                    .verticalScroll(scrollState)
                    // 2. Remove fixed height and use a weight-based approach or no height limit
                    // so it fits different aspect ratios
                    .fillMaxWidth()
            ) {
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

                // Role selector
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.daily_salary))
                    }
                    SegmentedButton(
                        selected = uiState.dialogSalaryType == SalaryType.MONTHLY,
                        onClick = { viewModel.updateDialogSalaryType(SalaryType.MONTHLY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
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

                OutlinedTextField(
                    value = uiState.dialogDescription,
                    onValueChange = viewModel::updateDialogDescription,
                    label = { Text(stringResource(R.string.description_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            Button( // Changed to filled Button for better primary action visibility
                onClick = viewModel::saveWorker,
                enabled = !uiState.isSaving && uiState.dialogName.isNotBlank() && uiState.dialogRole.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.isSaving) stringResource(R.string.saving) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissWorkerDialog) {
                Text(stringResource(R.string.cancel))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculateSalaryDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissSalaryDialog,
        icon = { Icon(Icons.Filled.Calculate, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.calculate_salary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Worker selector
                var workerExpanded by remember { mutableStateOf(false) }
                val selectedWorkerName =
                    uiState.workers.find { it.id == uiState.salaryDialogWorkerId }?.fullName ?: ""

                ExposedDropdownMenuBox(
                    expanded = workerExpanded,
                    onExpandedChange = { workerExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedWorkerName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.select_worker)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = workerExpanded,
                        onDismissRequest = { workerExpanded = false },
                    ) {
                        uiState.workers.filter { it.active }.forEach { worker ->
                            DropdownMenuItem(
                                text = { Text("${worker.fullName} (${worker.workerId})") },
                                onClick = {
                                    viewModel.updateSalaryDialogWorkerId(worker.id)
                                    workerExpanded = false
                                },
                            )
                        }
                    }
                }

                // Period type
                Text(
                    stringResource(R.string.select_period),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "DAY" to R.string.day_period,
                        "WEEK" to R.string.week_period,
                        "MONTH" to R.string.month_period
                    )
                        .forEachIndexed { index, (type, labelRes) ->
                            SegmentedButton(
                                selected = uiState.salaryDialogPeriodType == type,
                                onClick = { viewModel.updateSalaryDialogPeriodType(type) },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                            ) {
                                Text(
                                    stringResource(labelRes),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                }

                OutlinedTextField(
                    value = uiState.salaryDialogStartDate,
                    onValueChange = viewModel::updateSalaryDialogStartDate,
                    label = { Text(stringResource(R.string.period_start)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = uiState.salaryDialogEndDate,
                    onValueChange = viewModel::updateSalaryDialogEndDate,
                    label = { Text(stringResource(R.string.period_end)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::calculateSalary,
                enabled = !uiState.isSaving &&
                        uiState.salaryDialogWorkerId.isNotBlank() &&
                        uiState.salaryDialogStartDate.isNotBlank() &&
                        uiState.salaryDialogEndDate.isNotBlank(),
            ) {
                Text(if (uiState.isSaving) stringResource(R.string.saving) else stringResource(R.string.calculate_salary))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissSalaryDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PayNoteDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissPayNoteDialog,
        title = { Text(stringResource(R.string.mark_as_paid)) },
        text = {
            OutlinedTextField(
                value = uiState.paymentNote,
                onValueChange = viewModel::updatePaymentNote,
                label = { Text(stringResource(R.string.payment_note)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::markPaid) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissPayNoteDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateSalariesDialog(uiState: StaffViewModel.UiState, viewModel: StaffViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissGenerateDialog,
        icon = {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.generate_salaries)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.generate_salaries_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Period type
                Text(
                    stringResource(R.string.select_period),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "DAY" to R.string.day_period,
                        "WEEK" to R.string.week_period,
                        "MONTH" to R.string.month_period
                    ).forEachIndexed { index, (type, labelRes) ->
                        SegmentedButton(
                            selected = uiState.generatePeriodType == type,
                            onClick = { viewModel.updateGeneratePeriodType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                        ) {
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.generateStartDate,
                    onValueChange = viewModel::updateGenerateStartDate,
                    label = { Text(stringResource(R.string.period_start)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = uiState.generateEndDate,
                    onValueChange = viewModel::updateGenerateEndDate,
                    label = { Text(stringResource(R.string.period_end)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                uiState.generateResult?.let { result ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (uiState.generateResult != null) {
                TextButton(onClick = viewModel::dismissGenerateDialog) {
                    Text(stringResource(R.string.confirm))
                }
            } else {
                TextButton(
                    onClick = viewModel::generateSalaries,
                    enabled = !uiState.isSaving &&
                            uiState.generateStartDate.isNotBlank() &&
                            uiState.generateEndDate.isNotBlank(),
                ) {
                    Text(
                        if (uiState.isSaving) stringResource(R.string.generating)
                        else stringResource(R.string.generate_salaries)
                    )
                }
            }
        },
        dismissButton = {
            if (uiState.generateResult == null) {
                TextButton(onClick = viewModel::dismissGenerateDialog) {
                    Text(stringResource(R.string.cancel))
                }
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
