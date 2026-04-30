package net.marllex.waselak.feature.manager.analytics.components

import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.DoctorStats
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@Composable
fun DoctorSummarySection(doctors: List<DoctorStats>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.doctor_stats),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        if (doctors.isEmpty()) {
            Text(
                stringResource(Res.string.no_doctor_stats),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${doctors.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                        Text(stringResource(Res.string.doctor_stats), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${doctors.sumOf { it.prescriptionCount }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        Text(stringResource(Res.string.doctor_prescriptions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${kFormat("%.2f", doctors.sumOf { it.totalRevenue })}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                        Text(stringResource(Res.string.credit_revenue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Doctor list
            doctors.take(10).forEach { doctor ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text(doctor.doctorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${doctor.prescriptionCount} ${stringResource(Res.string.doctor_prescriptions)} · ${doctor.totalItems} ${stringResource(Res.string.doctor_items)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "${kFormat("%.2f", doctor.totalRevenue)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                        )
                    }
                }
            }
        }
    }
}
