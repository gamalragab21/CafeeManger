package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Desktop BiometricAuthenticator — triggers the OS system authentication prompt.
 *
 * - **macOS**: Uses a bundled Swift CLI tool that calls LAContext.evaluatePolicy()
 *   → Touch ID / system password dialog (same API as iOS)
 * - **Windows**: Uses PowerShell + .NET UserConsentVerifier → Windows Hello (face/fingerprint/PIN)
 * - **Linux**: Uses pkexec → polkit password dialog
 */
actual class BiometricAuthenticator {

    private val osName: String = System.getProperty("os.name", "").lowercase()

    actual fun isAvailable(): Boolean {
        return when {
            osName.contains("mac") -> checkMacOSAvailability()
            osName.contains("win") -> true // Windows Hello is widely available
            osName.contains("nux") || osName.contains("nix") -> checkLinuxAvailability()
            else -> false
        }
    }

    actual suspend fun authenticate(reason: String): BiometricResult {
        return withContext(Dispatchers.IO) {
            when {
                osName.contains("mac") -> authenticateMacOS(reason)
                osName.contains("win") -> authenticateWindows(reason)
                osName.contains("nux") || osName.contains("nix") -> authenticateLinux()
                else -> BiometricResult.NotAvailable
            }
        }
    }

    // ── macOS: Bundled Swift CLI → LAContext (Touch ID + password) ──────────

    private fun checkMacOSAvailability(): Boolean {
        return try {
            val binary = extractMacOSBinary() ?: return false
            binary.exists() && binary.canExecute()
        } catch (_: Exception) {
            false
        }
    }

    private fun authenticateMacOS(reason: String): BiometricResult {
        return try {
            val binary = extractMacOSBinary()
                ?: return BiometricResult.NotAvailable

            val process = ProcessBuilder(binary.absolutePath, reason)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            when (exitCode) {
                0 -> BiometricResult.Success
                1 -> BiometricResult.Cancelled
                2 -> BiometricResult.NotAvailable
                else -> BiometricResult.Error("Authentication failed (code $exitCode)")
            }
        } catch (e: Exception) {
            BiometricResult.Error(e.message ?: "macOS authentication error")
        }
    }

    /**
     * Extracts the bundled macOS biometric binary from JAR resources to a temp file.
     * The binary is cached in the system temp directory and reused across calls.
     */
    private fun extractMacOSBinary(): File? {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "waselak-auth")
        val binaryFile = File(tempDir, "macos-biometric-auth")

        if (binaryFile.exists() && binaryFile.canExecute()) {
            return binaryFile
        }

        return try {
            tempDir.mkdirs()
            val resourceStream = this::class.java.getResourceAsStream("/native/macos-biometric-auth")
                ?: return null

            resourceStream.use { input ->
                binaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Make executable
            try {
                Files.setPosixFilePermissions(
                    binaryFile.toPath(),
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                    ),
                )
            } catch (_: Exception) {
                binaryFile.setExecutable(true, false)
            }

            binaryFile
        } catch (_: Exception) {
            null
        }
    }

    // ── Windows: PowerShell → UserConsentVerifier (Windows Hello) ───────────

    private fun authenticateWindows(reason: String): BiometricResult {
        return try {
            // Use .NET UserConsentVerifier via PowerShell for Windows Hello
            val script = """
                Add-Type -AssemblyName Windows.Security
                ${'$'}result = [Windows.Security.Credentials.UI.UserConsentVerifier]::RequestVerificationAsync('$reason').GetAwaiter().GetResult()
                if (${'$'}result -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::Verified) {
                    exit 0
                } elseif (${'$'}result -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::Canceled) {
                    exit 1
                } elseif (${'$'}result -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::DeviceNotPresent -or
                          ${'$'}result -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::NotConfiguredForUser) {
                    exit 2
                } else {
                    exit 3
                }
            """.trimIndent()

            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            when (exitCode) {
                0 -> BiometricResult.Success
                1 -> BiometricResult.Cancelled
                2 -> BiometricResult.NotAvailable
                else -> BiometricResult.Error("Windows Hello authentication failed")
            }
        } catch (e: Exception) {
            BiometricResult.Error(e.message ?: "Windows authentication error")
        }
    }

    // ── Linux: pkexec → polkit password dialog ─────────────────────────────

    private fun checkLinuxAvailability(): Boolean {
        return try {
            val process = ProcessBuilder("which", "pkexec")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun authenticateLinux(): BiometricResult {
        return try {
            val user = System.getProperty("user.name")
            val process = ProcessBuilder("pkexec", "--user", user, "/bin/true")
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            when (exitCode) {
                0 -> BiometricResult.Success
                126 -> BiometricResult.Cancelled // dismissed
                127 -> BiometricResult.Error("Not authorized")
                else -> BiometricResult.Error("Linux authentication failed (code $exitCode)")
            }
        } catch (e: Exception) {
            BiometricResult.Error(e.message ?: "Linux authentication error")
        }
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    return remember { BiometricAuthenticator() }
}
