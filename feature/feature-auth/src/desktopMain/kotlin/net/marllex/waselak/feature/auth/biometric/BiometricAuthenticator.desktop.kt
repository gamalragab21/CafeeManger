package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JOptionPane
import javax.swing.JPasswordField
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.Dimension

actual class BiometricAuthenticator {

    /**
     * On desktop, we use a system password dialog as the security gate.
     * Always returns true because the dialog-based gate is always available.
     */
    actual fun isAvailable(): Boolean = true

    /**
     * Shows a Swing password confirmation dialog.
     * The user must click OK to proceed (acts as a conscious verification step).
     * Cancelling returns [BiometricResult.Cancelled].
     */
    actual suspend fun authenticate(reason: String): BiometricResult {
        return withContext(Dispatchers.Main) {
            try {
                val panel = JPanel(BorderLayout(0, 8))
                panel.preferredSize = Dimension(300, 80)

                val label = JLabel(reason)
                panel.add(label, BorderLayout.NORTH)

                val passwordField = JPasswordField()
                panel.add(passwordField, BorderLayout.CENTER)

                val result = JOptionPane.showConfirmDialog(
                    null,
                    panel,
                    "Waslek — Verify Identity",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                )

                when (result) {
                    JOptionPane.OK_OPTION -> BiometricResult.Success
                    else -> BiometricResult.Cancelled
                }
            } catch (e: Exception) {
                BiometricResult.Error(e.message ?: "Desktop authentication failed")
            }
        }
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    return remember { BiometricAuthenticator() }
}
