package net.marllex.waselak.feature.manager.analytics.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.feature.manager.analytics.AnalyticsViewModel.TimePeriod
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalFilterBar(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit,
    customFromDate: Long? = null,
    customToDate: Long? = null,
    onCustomDateRange: ((Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var pendingFrom by remember { mutableStateOf<Long?>(null) }

    val periods = listOf(
        TimePeriod.TODAY to stringResource(Res.string.today),
        TimePeriod.YESTERDAY to stringResource(Res.string.yesterday),
        TimePeriod.THIS_WEEK to stringResource(Res.string.this_week),
        TimePeriod.LAST_7_DAYS to stringResource(Res.string.last_7_days),
        TimePeriod.LAST_14_DAYS to stringResource(Res.string.last_14_days),
        TimePeriod.THIS_MONTH to stringResource(Res.string.this_month),
        TimePeriod.LAST_MONTH to stringResource(Res.string.last_month),
        TimePeriod.LAST_3_MONTHS to stringResource(Res.string.last_3_months),
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            periods.forEach { (period, label) ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onPeriodSelected(period) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(20.dp),
                )
            }
            // Custom date range chip
            FilterChip(
                selected = selectedPeriod == TimePeriod.CUSTOM,
                onClick = { showFromPicker = true },
                label = { Text(stringResource(Res.string.custom), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                shape = RoundedCornerShape(20.dp),
            )
        }

        // Show selected custom date range as an interactive chip
        if (selectedPeriod == TimePeriod.CUSTOM && customFromDate != null && customToDate != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tz = TimeZone.currentSystemDefault()
                val fromStr = Instant.fromEpochMilliseconds(customFromDate)
                    .toLocalDateTime(tz).date.toString()
                val toStr = Instant.fromEpochMilliseconds(customToDate)
                    .toLocalDateTime(tz).date.toString()
                AssistChip(
                    onClick = { showFromPicker = true },
                    label = {
                        Text(
                            "$fromStr  →  $toStr",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }

    // From Date Picker Dialog
    if (showFromPicker) {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val fromPickerState = rememberDatePickerState(
            initialSelectedDateMillis = customFromDate ?: nowMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingFrom = fromPickerState.selectedDateMillis
                    showFromPicker = false
                    showToPicker = true
                }) {
                    Text(stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            DatePicker(
                state = fromPickerState,
                title = {
                    Text(
                        stringResource(Res.string.select_start_date),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }

    // To Date Picker Dialog
    if (showToPicker) {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val toPickerState = rememberDatePickerState(
            initialSelectedDateMillis = customToDate ?: nowMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val from = pendingFrom
                    val to = toPickerState.selectedDateMillis
                    if (from != null && to != null && onCustomDateRange != null) {
                        onCustomDateRange(from, to)
                    }
                    showToPicker = false
                }) {
                    Text(stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            DatePicker(
                state = toPickerState,
                title = {
                    Text(
                        stringResource(Res.string.select_end_date),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
