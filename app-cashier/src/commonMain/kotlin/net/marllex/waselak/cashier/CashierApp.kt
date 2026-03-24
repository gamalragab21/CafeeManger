package net.marllex.waselak.cashier

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import net.marllex.waselak.cashier.navigation.CashierNavHost
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.ui.components.MandatoryUpdateScreen
import net.marllex.waselak.core.ui.theme.WaselakTheme
import org.koin.compose.koinInject

@Composable
fun CashierApp() {
    val authRepository: AuthRepository = koinInject()
    val vendorRepository: VendorRepository = koinInject()
    val offlineModeManager: OfflineModeManager = koinInject()
    val apiClient: WaselakApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var mandatoryUpdate by remember { mutableStateOf<MandatoryUpdateState?>(null) }

    // Check for mandatory update on app start
    LaunchedEffect(Unit) {
        try {
            val resp = apiClient.checkForUpdate(
                app = "cashier",
                version = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
            )
            if (resp.hasUpdate && resp.updateStatus == "MANDATORY") {
                mandatoryUpdate = MandatoryUpdateState(
                    latestVersion = resp.latestVersion,
                    releaseNotes = resp.releaseNotesAr ?: resp.releaseNotes,
                    downloadUrl = resp.downloadUrl,
                )
            }
        } catch (_: Exception) {
            // Can't check — allow app to continue
        }
    }

    WaselakTheme {
        val update = mandatoryUpdate
        if (update != null) {
            MandatoryUpdateScreen(
                appName = "Waselak Cashier",
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = update.latestVersion,
                releaseNotes = update.releaseNotes,
                downloadUrl = update.downloadUrl,
                // onDownload uses PlatformActions.openUrl() by default
            )
        } else {
            CashierNavHost(
                authRepository = authRepository,
                vendorRepository = vendorRepository,
                offlineModeManager = offlineModeManager,
            )
        }
    }
}

private data class MandatoryUpdateState(
    val latestVersion: String,
    val releaseNotes: String?,
    val downloadUrl: String?,
)
