package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.cancel
import waselak.core.core_ui.generated.resources.signout
import waselak.core.core_ui.generated.resources.signout_confirm

@Composable
fun SignOutButton(
    onSignOut: () -> Unit,
    signOutLabel: String = "",
    confirmMessage: String = "",
    cancelLabel: String = "",
    modifier: Modifier = Modifier,
) {
    val resolvedSignOutLabel = signOutLabel.ifEmpty { stringResource(Res.string.signout) }
    val resolvedConfirmMessage = confirmMessage.ifEmpty { stringResource(Res.string.signout_confirm) }
    val resolvedCancelLabel = cancelLabel.ifEmpty { stringResource(Res.string.cancel) }

    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = resolvedSignOutLabel,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(resolvedSignOutLabel) },
            text = { Text(resolvedConfirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onSignOut()
                }) {
                    Text(
                        resolvedSignOutLabel,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(resolvedCancelLabel)
                }
            },
        )
    }
}
