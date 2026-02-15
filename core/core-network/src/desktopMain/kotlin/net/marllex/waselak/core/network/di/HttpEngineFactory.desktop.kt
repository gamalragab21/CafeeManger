package net.marllex.waselak.core.network.di

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun httpEngineFactory(): HttpClientEngineFactory<*> = CIO
