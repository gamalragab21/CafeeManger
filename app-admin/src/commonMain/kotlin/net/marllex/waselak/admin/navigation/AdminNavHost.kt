package net.marllex.waselak.admin.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.session.AdminSessionManager
import net.marllex.waselak.admin.ui.screens.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

private data class NavItem(
    val labelRes: StringResource,
    val icon: ImageVector,
)

// Logs tab removed — 6 items instead of 7
private val navItems = listOf(
    NavItem(Res.string.nav_home, Icons.Default.Home),           // 0
    NavItem(Res.string.nav_vendors, Icons.Default.Store),       // 1
    NavItem(Res.string.nav_plans, Icons.Default.CreditCard),    // 2
    NavItem(Res.string.nav_analytics, Icons.Default.BarChart),  // 3
    NavItem(Res.string.nav_notifications, Icons.Default.Notifications), // 4
    NavItem(Res.string.nav_settings, Icons.Default.Settings),   // 5
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateVendor by remember { mutableStateOf(false) }
    var vendorDetailId by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Back handler: detail → tab view, tab → home, home → close
    val onBack: () -> Unit = {
        when {
            showCreateVendor -> { showCreateVendor = false }
            vendorDetailId != null -> { vendorDetailId = null }
            selectedTab != 0 -> { selectedTab = 0 }
            // else: system handles app close
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
            ) {
                // Drawer header
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

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
                            onClick = {
                                selectedTab = index
                                showCreateVendor = false
                                vendorDetailId = null
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                            modifier = Modifier.padding(vertical = 2.dp),
                            colors = NavigationDrawerItemDefaults.colors(),
                        )
                    }
                }
            }
        },
        gesturesEnabled = !showCreateVendor && vendorDetailId == null,
    ) {
        Scaffold(
            topBar = {
                if (!showCreateVendor && vendorDetailId == null) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(navItems[selectedTab].labelRes),
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                ScreenContent(
                    selectedTab = selectedTab,
                    showCreateVendor = showCreateVendor,
                    vendorDetailId = vendorDetailId,
                    onLogout = onLogout,
                    onCreateVendor = { showCreateVendor = true },
                    onVendorClick = { vendorDetailId = it },
                    onBack = onBack,
                )
            }
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
            5 -> SettingsScreen(onLogout = onLogout)
        }
    }
}
