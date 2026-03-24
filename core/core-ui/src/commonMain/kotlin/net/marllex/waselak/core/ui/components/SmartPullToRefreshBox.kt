package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * A pull-to-refresh wrapper that only shows the refresh indicator
 * when the user explicitly pulls down — not during initial load.
 * This prevents the indicator from showing on every loading state
 * and avoids accidental refresh triggers during scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPullToRefreshBox(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            onRefresh()
        },
        modifier = modifier,
        content = content,
    )
}
