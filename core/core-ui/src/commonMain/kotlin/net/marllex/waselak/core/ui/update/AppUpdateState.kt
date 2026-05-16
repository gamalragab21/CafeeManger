package net.marllex.waselak.core.ui.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.core.ui.platform.PlatformActions

/**
 * Single source of truth for the in-app self-update flow.
 *
 * Lifecycle:
 *   1. App starts → call [refresh] silently. If the backend reports an
 *      update, [updateInfo] holds the details + the banner appears.
 *   2. User taps "Install" → [startDownload] fetches the APK to a
 *      platform-appropriate location.
 *   3. Download completes → we automatically call
 *      [PlatformActions.installAppUpdate], which on Android fires the
 *      system "Install this package?" dialog and on desktop opens the
 *      downloaded installer.
 *   4. User cancels → [dismiss] hides the banner until next session.
 *
 * Holding this in a singleton (`single { … }` in Koin) means the banner
 * survives navigation: the user can keep using the app while the
 * background download runs, then get a fresh "Install" prompt when it
 * finishes.
 *
 * NOTE: this class is platform-agnostic. The actual HTTP call to
 * `/check-update` is delegated to the [check] lambda the caller passes
 * in, since the backend client lives in core-network (which would be
 * a circular dep if pulled directly).
 */
class AppUpdateState(
    private val platformActions: PlatformActions,
    private val check: suspend () -> CheckResult,
    private val appName: String,
) {
    /**
     * Result of a single backend `/check-update` call. Mirrors the
     * shape of the server's `CheckUpdateResponse` DTO but lives in
     * core-ui to keep this module's dependency surface small.
     */
    data class CheckResult(
        val hasUpdate: Boolean,
        val latestVersion: String?,
        val latestVersionCode: Int?,
        val updateStatus: String?,      // "UP_TO_DATE" | "OPTIONAL" | "MANDATORY"
        val releaseNotes: String?,
        val releaseNotesAr: String?,
        val downloadUrl: String?,       // absolute or relative-to-baseUrl
        val downloadFilename: String?,
        val baseUrl: String?,           // backend host (for relative downloadUrl resolution)
    )

    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data class Available(val info: CheckResult) : Phase
        data class Downloading(val info: CheckResult, val progress: Float) : Phase
        data class ReadyToInstall(val info: CheckResult, val filePath: String) : Phase
        data class Error(val message: String, val info: CheckResult?) : Phase
        data object Dismissed : Phase
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /** Whether the current state should block the rest of the UI. */
    val isMandatoryBlocking: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        scope.launch {
            phase.collect { p ->
                val mandatory = when (p) {
                    is Phase.Available -> p.info.updateStatus == "MANDATORY"
                    is Phase.Downloading -> p.info.updateStatus == "MANDATORY"
                    is Phase.ReadyToInstall -> p.info.updateStatus == "MANDATORY"
                    is Phase.Error -> p.info?.updateStatus == "MANDATORY"
                    else -> false
                }
                flow.value = mandatory
            }
        }
    }.asStateFlow()

    /**
     * Run the backend check. Idempotent — calling this multiple times
     * just refreshes the cached result. Errors are swallowed (logged
     * by caller) so the banner never appears for a transient network
     * failure.
     */
    fun refresh() {
        // Don't clobber an in-progress download with a fresh check.
        val current = _phase.value
        if (current is Phase.Downloading || current is Phase.ReadyToInstall) return
        scope.launch {
            _phase.value = Phase.Checking
            try {
                val result = check()
                _phase.value = when {
                    result.hasUpdate -> Phase.Available(result)
                    else -> Phase.Idle
                }
            } catch (_: Throwable) {
                _phase.value = Phase.Idle
            }
        }
    }

    /**
     * User tapped "Install" on the banner. Downloads + auto-fires the
     * install intent / opens the installer on completion.
     */
    fun startDownload() {
        val current = _phase.value
        val info = when (current) {
            is Phase.Available -> current.info
            is Phase.Error -> current.info ?: return
            is Phase.ReadyToInstall -> {
                // Already downloaded — just re-fire install.
                platformActions.installAppUpdate(current.filePath)
                return
            }
            else -> return
        }
        val rawUrl = info.downloadUrl ?: return
        val filename = info.downloadFilename
            ?: rawUrl.substringAfterLast('/').substringBefore('?').ifBlank { "update-${appName}.apk" }
        val fullUrl = if (rawUrl.startsWith("http", ignoreCase = true)) rawUrl
        else "${(info.baseUrl ?: "").trimEnd('/')}/${rawUrl.trimStart('/')}"

        scope.launch {
            _phase.value = Phase.Downloading(info, 0f)
            val path = try {
                platformActions.downloadAppUpdate(fullUrl, filename) { p ->
                    _phase.value = Phase.Downloading(info, p)
                }
            } catch (e: Throwable) {
                _phase.value = Phase.Error(e.message ?: "Download failed", info)
                return@launch
            }
            if (path.isNullOrBlank()) {
                _phase.value = Phase.Error("Download failed", info)
                return@launch
            }
            // Fire install immediately. The user already consented by
            // tapping Install; auto-prompting the OS dialog skips one
            // user-action step.
            val ok = platformActions.installAppUpdate(path)
            _phase.value = if (ok) Phase.ReadyToInstall(info, path)
            else Phase.Error("Install intent failed", info)
        }
    }

    /** User tapped "Later" on the banner. Hidden until next refresh(). */
    fun dismiss() {
        // Mandatory updates can't be dismissed — the dialog remains.
        if (isMandatoryBlocking.value) return
        _phase.value = Phase.Dismissed
    }
}
