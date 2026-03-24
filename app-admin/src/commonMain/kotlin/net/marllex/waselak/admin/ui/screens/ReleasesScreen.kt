package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.AppReleaseDto
import net.marllex.waselak.admin.network.CreateReleaseRequest
import net.marllex.waselak.admin.network.UpdateReleaseRequest
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen() {
    val apiClient = koinInject<AdminApiClient>()
    val scope = rememberCoroutineScope()

    var releases by remember { mutableStateOf<List<AppReleaseDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Load releases
    LaunchedEffect(Unit) {
        isLoading = true
        releases = apiClient.getReleases()
        isLoading = false
    }

    fun refresh() {
        scope.launch {
            isLoading = true
            releases = apiClient.getReleases()
            isLoading = false
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Release")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Header
            Text(
                "App Releases",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
            )

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                releases.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NewReleases, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Text("No releases yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Create your first release to enable auto-updates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(releases, key = { it.id }) { release ->
                            ReleaseCard(
                                release = release,
                                onToggleStatus = { newStatus ->
                                    scope.launch {
                                        apiClient.updateRelease(release.id, UpdateReleaseRequest(update_status = newStatus))
                                        refresh()
                                    }
                                },
                                onToggleActive = { active ->
                                    scope.launch {
                                        apiClient.updateRelease(release.id, UpdateReleaseRequest(is_active = active))
                                        refresh()
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        apiClient.deleteRelease(release.id)
                                        refresh()
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Create Release Dialog
    if (showCreateDialog) {
        CreateReleaseDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { request ->
                scope.launch {
                    apiClient.createRelease(request)
                    showCreateDialog = false
                    refresh()
                }
            },
        )
    }
}

@Composable
private fun ReleaseCard(
    release: AppReleaseDto,
    onToggleStatus: (String) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when (release.update_status) {
        "MANDATORY" -> Color(0xFFE53935)
        "OPTIONAL" -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!release.is_active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = statusColor)
                    Text("v${release.version_name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("(${release.version_code})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        release.update_status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }

            // Release notes
            release.release_notes?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            release.release_notes_ar?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Min version
            Text("Min version code: ${release.min_version_code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = release.is_active, onCheckedChange = onToggleActive)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (release.update_status == "OPTIONAL") {
                        TextButton(onClick = { onToggleStatus("MANDATORY") }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))) {
                            Text("Make Mandatory")
                        }
                    } else {
                        TextButton(onClick = { onToggleStatus("OPTIONAL") }) {
                            Text("Make Optional")
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateReleaseDialog(
    onDismiss: () -> Unit,
    onCreate: (CreateReleaseRequest) -> Unit,
) {
    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var updateStatus by remember { mutableStateOf("OPTIONAL") }
    var releaseNotes by remember { mutableStateOf("") }
    var releaseNotesAr by remember { mutableStateOf("") }
    var minVersionCode by remember { mutableStateOf("1") }
    var driveFolderId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Release", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text("Version (e.g. 1.2.0)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { versionCode = it },
                        label = { Text("Code") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                }

                // Update status
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = updateStatus == "OPTIONAL", onClick = { updateStatus = "OPTIONAL" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text("Optional")
                    }
                    SegmentedButton(selected = updateStatus == "MANDATORY", onClick = { updateStatus = "MANDATORY" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text("Mandatory")
                    }
                }

                OutlinedTextField(
                    value = releaseNotes,
                    onValueChange = { releaseNotes = it },
                    label = { Text("Release Notes (EN)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = releaseNotesAr,
                    onValueChange = { releaseNotesAr = it },
                    label = { Text("Release Notes (AR)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = minVersionCode,
                    onValueChange = { minVersionCode = it },
                    label = { Text("Min Version Code (below = forced)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                // Drive folder ID removed — URL is auto-calculated from naming convention
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(CreateReleaseRequest(
                        version_name = versionName.trim(),
                        version_code = versionCode.toIntOrNull() ?: 1,
                        update_status = updateStatus,
                        release_notes = releaseNotes.ifBlank { null },
                        release_notes_ar = releaseNotesAr.ifBlank { null },
                        min_version_code = minVersionCode.toIntOrNull() ?: 1,
                        drive_folder_id = driveFolderId.ifBlank { null },
                    ))
                },
                enabled = versionName.isNotBlank() && versionCode.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
