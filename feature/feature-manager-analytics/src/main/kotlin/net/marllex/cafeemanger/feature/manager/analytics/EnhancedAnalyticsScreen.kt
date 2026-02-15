package net.marllex.cafeemanger.feature.manager.analytics

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAnalyticsScreen(
    onNavigateToExport: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    // Use the simplified, user-friendly analytics screen
    SimplifiedAnalyticsScreen(
        viewModel = viewModel,
        onNavigateToExport = onNavigateToExport
    )
}
