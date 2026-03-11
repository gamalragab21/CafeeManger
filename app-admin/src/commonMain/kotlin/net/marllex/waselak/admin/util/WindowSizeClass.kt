package net.marllex.waselak.admin.util

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material3-aligned width size classes for adaptive layout.
 *
 * - COMPACT  (< 600dp) — phones
 * - MEDIUM   (600–840dp) — tablets portrait, small desktop windows
 * - EXPANDED (> 840dp) — tablets landscape, desktop, web
 */
enum class WindowWidthSizeClass { COMPACT, MEDIUM, EXPANDED }

fun WindowWidthSizeClass(width: Dp): WindowWidthSizeClass = when {
    width < 600.dp -> WindowWidthSizeClass.COMPACT
    width < 840.dp -> WindowWidthSizeClass.MEDIUM
    else -> WindowWidthSizeClass.EXPANDED
}

/**
 * CompositionLocal provided by AdminNavHost MainScaffold.
 * Screens read this to adapt their layouts.
 */
val LocalWindowSizeClass = compositionLocalOf { WindowWidthSizeClass.COMPACT }
