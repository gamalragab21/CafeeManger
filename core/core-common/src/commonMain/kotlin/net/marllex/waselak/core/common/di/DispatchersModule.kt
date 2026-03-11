package net.marllex.waselak.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

enum class WaselakDispatchers {
    Default,
    IO,
    Main
}

internal expect val ioDispatcher: CoroutineDispatcher

val dispatchersModule = module {
    single<CoroutineDispatcher>(named(WaselakDispatchers.IO.name)) { ioDispatcher }
    single<CoroutineDispatcher>(named(WaselakDispatchers.Default.name)) { Dispatchers.Default }
    single<CoroutineDispatcher>(named(WaselakDispatchers.Main.name)) { Dispatchers.Main }
}
