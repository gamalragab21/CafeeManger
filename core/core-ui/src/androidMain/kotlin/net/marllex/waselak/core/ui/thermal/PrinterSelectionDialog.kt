package net.marllex.waselak.core.ui.thermal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom-sheet-like dialog that lists Bluetooth + USB ESC/POS printers
 * and lets the cashier pick one. The choice is persisted via
 * [ThermalPrinterManager.savePrinter] so subsequent prints go silently.
 *
 * Lifecycle:
 *   1. Compose mount → request BLUETOOTH_CONNECT runtime permission on
 *      Android 12+ if not already granted.
 *   2. As soon as permission resolves, run [ThermalPrinterManager.discoverPrinters]
 *      on a background dispatcher (IO).
 *   3. Render the result. Tapping a row + "Save" calls [onSelected]
 *      with the chosen [PrinterConfig].
 *
 * Caller is responsible for hiding the dialog when [onDismiss] or
 * [onSelected] fires.
 */
@Composable
fun PrinterSelectionDialog(
    manager: ThermalPrinterManager,
    onSelected: (PrinterConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // We need BOTH BLUETOOTH_CONNECT *and* BLUETOOTH_SCAN at runtime
    // on Android 12+. CONNECT alone isn't enough — the Dantsu library's
    // BluetoothConnection.connect() calls BluetoothAdapter.cancelDiscovery()
    // during the connection setup, which throws a SecurityException
    // without SCAN. We saw this exact crash in production:
    //   "Need android.permission.BLUETOOTH_SCAN permission … at
    //    BluetoothAdapter.cancelDiscovery"
    // Requesting both upfront means a single system dialog and the
    // cashier doesn't see a "Print failed" toast on first use.
    val requiredBluetoothPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        emptyArray() // legacy BLUETOOTH/BLUETOOTH_ADMIN are install-time
    }

    var hasBluetoothPermission by remember {
        mutableStateOf(
            requiredBluetoothPerms.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            },
        )
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasBluetoothPermission = result.values.all { it }
    }

    var isScanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf<String?>(manager.getSavedPrinter()?.address) }

    // Trigger an initial scan once we have permission.
    //
    // Before scanning, we kick off any pending USB-permission requests.
    // The OS shows its own "Allow Waselak Cashier to access this USB
    // device?" dialog; once the user taps Allow, a quick rescan picks
    // the device up. If no USB devices are plugged in (or all of them
    // are already permissioned), this is a no-op.
    LaunchedEffect(hasBluetoothPermission) {
        if (!hasBluetoothPermission) {
            if (requiredBluetoothPerms.isNotEmpty()) {
                btPermissionLauncher.launch(requiredBluetoothPerms)
            }
            return@LaunchedEffect
        }
        val requested = manager.requestUsbPermissions()
        if (requested > 0) {
            // Give the OS dialog a moment to surface + the user a
            // moment to tap Allow before we run the discovery sweep.
            kotlinx.coroutines.delay(1500)
        }
        isScanning = true
        devices = withContext(Dispatchers.IO) { manager.discoverPrinters() }
        isScanning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Print,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        },
        title = {
            Text(
                text = "اختر الطابعة / Select printer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Header row: status + manual rescan button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            !hasBluetoothPermission -> "Bluetooth permission required"
                            isScanning -> "Scanning…"
                            devices.isEmpty() -> "No printers found. Make sure your printer is paired (BT) or plugged in (USB)."
                            else -> "${devices.size} printer(s) found"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                // Re-request USB perms on rescan too —
                                // catches printers the user plugged in
                                // AFTER the dialog opened.
                                val requested = manager.requestUsbPermissions()
                                if (requested > 0) kotlinx.coroutines.delay(1500)
                                isScanning = true
                                devices = withContext(Dispatchers.IO) { manager.discoverPrinters() }
                                isScanning = false
                            }
                        },
                        enabled = hasBluetoothPermission && !isScanning,
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Device list
                if (devices.isEmpty() && !isScanning && hasBluetoothPermission) {
                    Text(
                        "Pair a Bluetooth thermal printer in Android Settings → Bluetooth, then tap Rescan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(devices, key = { "${it.type}:${it.address}" }) { dev ->
                            PrinterRow(
                                device = dev,
                                isSelected = dev.address == selectedAddress,
                                onClick = { selectedAddress = dev.address },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = devices.firstOrNull { it.address == selectedAddress }
                    if (chosen != null) {
                        val cfg = chosen.toConfig()
                        manager.savePrinter(cfg)
                        onSelected(cfg)
                    }
                },
                enabled = selectedAddress != null,
            ) { Text("استخدم / Use") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء / Cancel") }
        },
    )
}

@Composable
private fun PrinterRow(
    device: DiscoveredPrinter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (device.type == "bluetooth") Icons.Default.Bluetooth else Icons.Default.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${device.type.uppercase()} • ${device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
