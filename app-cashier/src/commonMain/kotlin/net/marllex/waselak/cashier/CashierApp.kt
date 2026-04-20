package net.marllex.waselak.cashier

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import net.marllex.waselak.cashier.di.ensureCashierFeaturesLoaded
import net.marllex.waselak.cashier.navigation.CashierNavHost
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.CategoryRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.TableRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.ui.components.MandatoryUpdateScreen
import net.marllex.waselak.core.ui.theme.WaselakTheme
import org.koin.compose.koinInject

@Composable
fun CashierApp() {
    // Register the post-login feature VMs before the nav host tries to inject any.
    // Idempotent + cheap — only the first call actually loads the module.
    remember { ensureCashierFeaturesLoaded() }

    val authRepository: AuthRepository = koinInject()
    val vendorRepository: VendorRepository = koinInject()
    val offlineModeManager: OfflineModeManager = koinInject()
    val apiClient: WaselakApiClient = koinInject()
    val itemRepository: ItemRepository = koinInject()
    val categoryRepository: CategoryRepository = koinInject()
    val tableRepository: TableRepository = koinInject()
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

    // Splash-time prefetch. The moment we detect a valid session, fan out refresh calls
    // for the data the POS screen needs on first render (items + categories + tables).
    // By the time the user taps past the nav graph, the local DB already has fresh rows
    // and PosScreen opens with a warm cache instead of a spinner. Failures are silent —
    // if we're offline, the existing cached data is what POS will display anyway.
    LaunchedEffect(Unit) {
        authRepository.isLoggedIn
            .distinctUntilChanged()
            .filter { it }
            .collect {
                AppLogger.d("CashierApp", "Prefetch: warming POS data caches")
                // Run refreshes in parallel — order doesn't matter, each is independent.
                launch { itemRepository.refreshItems() }
                launch { categoryRepository.refreshCategories() }
                launch { tableRepository.refreshTables() }
                launch { vendorRepository.refreshVendor() }
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
