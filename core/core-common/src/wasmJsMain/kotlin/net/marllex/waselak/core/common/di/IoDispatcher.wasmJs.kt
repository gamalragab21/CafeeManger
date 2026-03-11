package net.marllex.waselak.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// wasmJs is single-threaded — use Default dispatcher as IO fallback
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
