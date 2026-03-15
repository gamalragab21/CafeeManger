package net.marllex.waselak.admin.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.session.AdminSessionManager
import net.marllex.waselak.admin.ui.screens.*
import net.marllex.waselak.admin.util.LocalWindowSizeClass
import net.marllex.waselak.admin.util.WindowWidthSizeClass
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

private data class NavItem(
    val labelRes: StringResource,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(Res.string.nav_home, Icons.Default.Home),
    NavItem(Res.string.nav_vendors, Icons.Default.Store),
    NavItem(Res.string.nav_plans, Icons.Default.CreditCard),
    NavItem(Res.string.nav_analytics, Icons.Default.BarChart),
    NavItem(Res.string.nav_notifications, Icons.Default.Notifications),
    NavItem(Res.string.nav_logs, Icons.Default.BugReport),
    NavItem(Res.string.nav_settings, Icons.Default.Settings),
)

@Composable
fun AdminNavHost() {
    val sessionManager = koinInject<AdminSessionManager>()
    val apiClient = koinInject<AdminApiClient>()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val savedToken = sessionManager.getToken()
        if (savedToken != null) {
            Logger.i("AdminNavHost") { "Found saved token, validating session..." }
            val profile = apiClient.getProfile()
            if (profile != null) {
                Logger.i("AdminNavHost") { "Session restored for ${profile.email}" }
                startDestination = "main"
            } else {
                Logger.w("AdminNavHost") { "Session invalid, clearing tokens" }
                sessionManager.clearToken()
                apiClient.clearToken()
                startDestination = "login"
            }
        } else {
            startDestination = "login"
        }
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            MainScaffold(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun MainScaffold(
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateVendor by remember { mutableStateOf(false) }
    var vendorDetailId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints {
        val widthClass = WindowWidthSizeClass(maxWidth)

        CompositionLocalProvider(LocalWindowSizeClass provides widthClass) {
            when (widthClass) {
                WindowWidthSizeClass.COMPACT -> CompactScaffold(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it; showCreateVendor = false; vendorDetailId = null },
                    showCreateVendor = showCreateVendor,
                    vendorDetailId = vendorDetailId,
                    onLogout = onLogout,
                    onCreateVendor = { showCreateVendor = true },
                    onVendorClick = { vendorDetailId = it },
                    onBack = { showCreateVendor = false; vendorDetailId = null },
                )

                WindowWidthSizeClass.MEDIUM -> MediumScaffold(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it; showCreateVendor = false; vendorDetailId = null },
                    showCreateVendor = showCreateVendor,
                    vendorDetailId = vendorDetailId,
                    onLogout = onLogout,
                    onCreateVendor = { showCreateVendor = true },
                    onVendorClick = { vendorDetailId = it },
                    onBack = { showCreateVendor = false; vendorDetailId = null },
                )

                WindowWidthSizeClass.EXPANDED -> ExpandedScaffold(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it; showCreateVendor = false; vendorDetailId = null },
                    showCreateVendor = showCreateVendor,
                    vendorDetailId = vendorDetailId,
                    onLogout = onLogout,
                    onCreateVendor = { showCreateVendor = true },
                    onVendorClick = { vendorDetailId = it },
                    onBack = { showCreateVendor = false; vendorDetailId = null },
                )
            }
        }
    }
}

// ── COMPACT (< 600dp): Bottom NavigationBar ─────────────────────────────────

@Composable
private fun CompactScaffold(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    showCreateVendor: Boolean,
    vendorDetailId: String?,
    onLogout: () -> Unit,
    onCreateVendor: () -> Unit,
    onVendorClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            if (!showCreateVendor && vendorDetailId == null) {
                NavigationBar {
                    navItems.forEachIndexed { index, item ->
                        val label = stringResource(item.labelRes)
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            ScreenContent(
                selectedTab = selectedTab,
                showCreateVendor = showCreateVendor,
                vendorDetailId = vendorDetailId,
                onLogout = onLogout,
                onCreateVendor = onCreateVendor,
                onVendorClick = onVendorClick,
                onBack = onBack,
            )
        }
    }
}

// ── MEDIUM (600–840dp): Navigation Rail ─────────────────────────────────────

@Composable
private fun MediumScaffold(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    showCreateVendor: Boolean,
    vendorDetailId: String?,
    onLogout: () -> Unit,
    onCreateVendor: () -> Unit,
    onVendorClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            NavigationRail {
                navItems.forEachIndexed { index, item ->
                    val label = stringResource(item.labelRes)
                    NavigationRailItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }

            ScreenContent(
                selectedTab = selectedTab,
                showCreateVendor = showCreateVendor,
                vendorDetailId = vendorDetailId,
                onLogout = onLogout,
                onCreateVendor = onCreateVendor,
                onVendorClick = onVendorClick,
                onBack = onBack,
            )
        }
    }
}

// ── EXPANDED (> 840dp): Permanent Navigation Drawer ─────────────────────────

@Composable
private fun ExpandedScaffold(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    showCreateVendor: Boolean,
    vendorDetailId: String?,
    onLogout: () -> Unit,
    onCreateVendor: () -> Unit,
    onVendorClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(
                modifier = Modifier.width(240.dp),
            ) {
                // Drawer header
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    navItems.forEachIndexed { index, item ->
                        val label = stringResource(item.labelRes)
                        NavigationDrawerItem(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                            modifier = Modifier.padding(vertical = 2.dp),
                            colors = NavigationDrawerItemDefaults.colors(),
                        )
                    }
                }
            }
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ScreenContent(
                selectedTab = selectedTab,
                showCreateVendor = showCreateVendor,
                vendorDetailId = vendorDetailId,
                onLogout = onLogout,
                onCreateVendor = onCreateVendor,
                onVendorClick = onVendorClick,
                onBack = onBack,
            )
        }
    }
}

// ── Shared content switcher ─────────────────────────────────────────────────

@Composable
private fun ScreenContent(
    selectedTab: Int,
    showCreateVendor: Boolean,
    vendorDetailId: String?,
    onLogout: () -> Unit,
    onCreateVendor: () -> Unit,
    onVendorClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    when {
        vendorDetailId != null -> {
            VendorDetailScreen(
                vendorId = vendorDetailId,
                onBack = { onBack() },
            )
        }
        showCreateVendor -> {
            CreateVendorScreen(
                onBack = { onBack() },
            )
        }
        else -> when (selectedTab) {
            0 -> HomeScreen()
            1 -> VendorsScreen(
                onCreateVendor = onCreateVendor,
                onVendorClick = onVendorClick,
            )
            2 -> PlansScreen()
            3 -> AnalyticsScreen()
            4 -> AdminNotificationsScreen()
            5 -> LogsDashboardScreen()
            6 -> SettingsScreen(onLogout = onLogout)
        }
    }
}
