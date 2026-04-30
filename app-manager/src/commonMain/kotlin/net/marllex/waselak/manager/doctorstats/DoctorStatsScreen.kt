package net.marllex.waselak.manager.doctorstats

import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.network.dto.DoctorStatsResponse
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorStatsScreen(
    viewModel: DoctorStatsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.doctor_stats),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.stats.isEmpty() -> LoadingIndicator()
            uiState.error != null && uiState.stats.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
            uiState.stats.isEmpty() -> EmptyView(stringResource(Res.string.no_doctor_stats))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.stats, key = { it.doctorName }) { doctor ->
                    DoctorCard(doctor)
                }
            }
        }
    }
}

@Composable
private fun DoctorCard(doctor: DoctorStatsResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(doctor.doctorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "${kFormat("%.2f", doctor.totalRevenue)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${doctor.prescriptionCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(Res.string.doctor_prescriptions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${doctor.totalItems}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(Res.string.doctor_items), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
