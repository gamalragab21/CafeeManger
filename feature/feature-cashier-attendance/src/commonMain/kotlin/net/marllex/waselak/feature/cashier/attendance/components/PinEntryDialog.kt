package net.marllex.waselak.feature.cashier.attendance.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.cashier.attendance.generated.resources.Res
import net.marllex.waselak.feature.cashier.attendance.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun PinEntryDialog(
    workerName: String,
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val maxPinLength = 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.enter_pin),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = workerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // PIN display (dots)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 24.dp),
                ) {
                    repeat(maxPinLength) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index < pin.length) {
                                Surface(
                                    modifier = Modifier.size(16.dp),
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {}
                            } else {
                                Surface(
                                    modifier = Modifier.size(16.dp),
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {}
                            }
                        }
                    }
                }

                // Numeric keypad
                NumericKeypad(
                    onNumberClick = { number ->
                        if (pin.length < maxPinLength) {
                            pin += number
                        }
                    },
                    onBackspace = {
                        if (pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                        }
                    },
                    onClear = {
                        pin = ""
                    },
                )

                Spacer(Modifier.height(8.dp))

                // PIN length hint
                Text(
                    text = stringResource(Res.string.pin_length_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPinEntered(pin) },
                enabled = pin.length in 4..6,
            ) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1: 1, 2, 3
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeypadButton("1", onClick = { onNumberClick("1") })
            KeypadButton("2", onClick = { onNumberClick("2") })
            KeypadButton("3", onClick = { onNumberClick("3") })
        }

        // Row 2: 4, 5, 6
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeypadButton("4", onClick = { onNumberClick("4") })
            KeypadButton("5", onClick = { onNumberClick("5") })
            KeypadButton("6", onClick = { onNumberClick("6") })
        }

        // Row 3: 7, 8, 9
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeypadButton("7", onClick = { onNumberClick("7") })
            KeypadButton("8", onClick = { onNumberClick("8") })
            KeypadButton("9", onClick = { onNumberClick("9") })
        }

        // Row 4: Clear, 0, Backspace
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "C",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            KeypadButton("0", onClick = { onNumberClick("0") })
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.Filled.Backspace,
                    contentDescription = stringResource(Res.string.backspace),
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
