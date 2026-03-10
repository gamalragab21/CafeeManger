package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.clear_logs
import waselak.core.core_ui.generated.resources.share_logs
import waselak.core.core_ui.generated.resources.upload_logs
import waselak.core.core_ui.generated.resources.upload_logs_description
import waselak.core.core_ui.generated.resources.uploading_logs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadLogsCard(
    isUploading: Boolean,
    onUploadLogs: () -> Unit,
    onShareLogs: (() -> Unit)? = null,
    onClearLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { if (!isUploading) onUploadLogs() },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isUploading) stringResource(Res.string.uploading_logs) else stringResource(Res.string.upload_logs),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.upload_logs_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            if (!isUploading) {
                if (onClearLogs != null) {
                    IconButton(onClick = onClearLogs) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(Res.string.clear_logs),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (onShareLogs != null) {
                    IconButton(onClick = onShareLogs) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = stringResource(Res.string.share_logs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
