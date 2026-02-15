package net.marllex.waselak.core.network.di

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun httpEngineFactory(): HttpClientEngineFactory<*> = Darwin
