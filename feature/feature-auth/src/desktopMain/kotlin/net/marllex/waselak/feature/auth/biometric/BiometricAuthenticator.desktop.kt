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
            osName.contains("win") -> checkWindowsHelloAvailable()
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

    // ── Windows: Check if any credential verification is available ────────

    private fun checkWindowsHelloAvailable(): Boolean = true // Always try — fallback handles failures

    // ── Windows: Try multiple auth methods ──────────────────────────────

    private fun authenticateWindows(reason: String): BiometricResult {
        // Method 1: Windows Hello (UserConsentVerifier — face/fingerprint/PIN)
        val helloResult = tryWindowsHello(reason)
        if (helloResult == BiometricResult.Success) return helloResult

        // Method 2: Windows Credential UI (asks for Windows login password/PIN)
        val credResult = tryWindowsCredentialPrompt(reason)
        if (credResult == BiometricResult.Success) return credResult

        // Method 3: If both failed, return the best error
        return when {
            helloResult is BiometricResult.NotAvailable && credResult is BiometricResult.NotAvailable ->
                BiometricResult.NotAvailable
            helloResult is BiometricResult.Cancelled || credResult is BiometricResult.Cancelled ->
                BiometricResult.Cancelled
            else -> BiometricResult.Error("Device verification failed")
        }
    }

    private fun tryWindowsHello(reason: String): BiometricResult {
        return try {
            val script = """
                try {
                    Add-Type -AssemblyName Windows.Security
                    ${'$'}result = [Windows.Security.Credentials.UI.UserConsentVerifier]::RequestVerificationAsync('$reason').GetAwaiter().GetResult()
                    if (${'$'}result -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::Verified) {
                        exit 0
                    } elseif (${'$'}result -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::Canceled) {
                        exit 1
                    } else {
                        exit 2
                    }
                } catch {
                    exit 2
                }
            """.trimIndent()
            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            when (exitCode) {
                0 -> BiometricResult.Success
                1 -> BiometricResult.Cancelled
                else -> BiometricResult.NotAvailable
            }
        } catch (_: Exception) {
            BiometricResult.NotAvailable
        }
    }

    private fun tryWindowsCredentialPrompt(reason: String): BiometricResult {
        return try {
            // Use CredentialUIBroker — native Windows credential dialog (supports PIN + password + smartcard)
            val script = """
                Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class CredUI {
    [DllImport("credui.dll", CharSet = CharSet.Unicode)]
    public static extern int CredUIPromptForWindowsCredentialsW(
        ref CREDUI_INFO info, int authError, ref uint authPackage,
        IntPtr inBuf, uint inBufSize, out IntPtr outBuf, out uint outBufSize,
        ref bool save, int flags);
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDUI_INFO {
        public int cbSize; public IntPtr hwnd; public string pszMessageText;
        public string pszCaptionText; public IntPtr hbmBanner;
    }
}
"@
                ${'$'}info = New-Object CredUI+CREDUI_INFO
                ${'$'}info.cbSize = [System.Runtime.InteropServices.Marshal]::SizeOf(${'$'}info)
                ${'$'}info.pszCaptionText = 'Waselak'
                ${'$'}info.pszMessageText = '$reason'
                ${'$'}authPkg = [uint32]0; ${'$'}save = ${'$'}false
                ${'$'}outBuf = [IntPtr]::Zero; ${'$'}outSize = [uint32]0
                ${'$'}result = [CredUI]::CredUIPromptForWindowsCredentialsW([ref]${'$'}info, 0, [ref]${'$'}authPkg, [IntPtr]::Zero, 0, [ref]${'$'}outBuf, [ref]${'$'}outSize, [ref]${'$'}save, 0x1)
                if (${'$'}result -eq 0) { exit 0 } elseif (${'$'}result -eq 1223) { exit 1 } else { exit 2 }
            """.trimIndent()
            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            when (exitCode) {
                0 -> BiometricResult.Success
                1 -> BiometricResult.Cancelled
                3 -> BiometricResult.Error("Wrong password")
                else -> BiometricResult.NotAvailable
            }
        } catch (_: Exception) {
            BiometricResult.NotAvailable
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
