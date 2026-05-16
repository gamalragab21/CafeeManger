package net.marllex.waselak.core.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Slim top-of-screen banner that surfaces app updates.
 *
 * Three visual states:
 *   • Available → "Update v1.12.0 available" + [Install] [Later]
 *   • Downloading → progress bar + percent
 *   • ReadyToInstall → "Tap to install" (re-fires install intent —
 *      useful if the user dismissed the system dialog and wants to
 *      retry without re-downloading)
 *
 * For MANDATORY updates the [UpdateBlockingDialog] is shown instead of
 * the banner — covers the whole screen, no Later button.
 *
 * Place this at the top of every scaffold (above the TopAppBar) so it
 * stays visible regardless of which screen the cashier is on.
 */
@Composable
fun UpdateBanner(state: AppUpdateState) {
    val phase by state.phase.collectAsState()
    val mandatory by state.isMandatoryBlocking.collectAsState()

    // Mandatory updates use the blocking dialog, not the banner.
    if (mandatory) {
        UpdateBlockingDialog(state)
        return
    }

    when (val p = phase) {
        is AppUpdateState.Phase.Available -> AvailableRow(
            version = p.info.latestVersion,
            onInstall = { state.startDownload() },
            onLater = { state.dismiss() },
        )
        is AppUpdateState.Phase.Downloading -> DownloadingRow(
            version = p.info.latestVersion,
            progress = p.progress,
        )
        is AppUpdateState.Phase.ReadyToInstall -> ReadyRow(
            version = p.info.latestVersion,
            onInstall = { state.startDownload() }, // re-fires install intent
        )
        is AppUpdateState.Phase.Error -> ErrorRow(
            message = p.message,
            onRetry = { state.startDownload() },
            onDismiss = { state.dismiss() },
        )
        else -> Unit
    }
}

@Composable
private fun AvailableRow(
    version: String?,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.NewReleases, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "تحديث متاح / Update available",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!version.isNullOrBlank()) {
                    Text(
                        text = "v$version",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onLater) {
                Text("لاحقاً / Later", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onInstall,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("تثبيت / Install", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DownloadingRow(version: String?, progress: Float) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (version != null) "جاري تنزيل v$version / Downloading v$version"
                    else "جاري التنزيل / Downloading",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun ReadyRow(version: String?, onInstall: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (version != null) "v$version جاهز للتثبيت / Tap to install v$version"
                else "جاهز للتثبيت / Tap to install",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onInstall,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("تثبيت / Install", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Error, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("إغلاق / Close") }
            TextButton(onClick = onRetry) { Text("إعادة / Retry") }
        }
    }
}

/**
 * Full-screen blocking dialog for MANDATORY updates. Cannot be
 * dismissed — the only path forward is to tap Install and complete
 * the update. Used when the backend reports `update_status: MANDATORY`
 * AND the user's `version_code` is below `min_version_code`.
 */
@Composable
private fun UpdateBlockingDialog(state: AppUpdateState) {
    val phase by state.phase.collectAsState()

    val info = when (val p = phase) {
        is AppUpdateState.Phase.Available -> p.info
        is AppUpdateState.Phase.Downloading -> p.info
        is AppUpdateState.Phase.ReadyToInstall -> p.info
        is AppUpdateState.Phase.Error -> p.info
        else -> null
    } ?: return

    AlertDialog(
        onDismissRequest = { /* no-op — mandatory */ },
        title = {
            Text(
                "تحديث إجباري / Update Required",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text("v${info.latestVersion} ${"إصدار جديد متاح / new version available"}")
                Spacer(Modifier.height(12.dp))
                when (val p = phase) {
                    is AppUpdateState.Phase.Downloading -> {
                        LinearProgressIndicator(
                            progress = { p.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${(p.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    is AppUpdateState.Phase.Error -> {
                        Text(
                            p.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> {
                        val notes = info.releaseNotesAr ?: info.releaseNotes
                        if (!notes.isNullOrBlank()) {
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { state.startDownload() },
                enabled = phase !is AppUpdateState.Phase.Downloading,
            ) {
                Text(
                    when (phase) {
                        is AppUpdateState.Phase.Downloading -> "جاري التنزيل... / Downloading..."
                        else -> "تثبيت الآن / Install Now"
                    },
                )
            }
        },
        // No dismissButton — mandatory means mandatory.
    )
}
