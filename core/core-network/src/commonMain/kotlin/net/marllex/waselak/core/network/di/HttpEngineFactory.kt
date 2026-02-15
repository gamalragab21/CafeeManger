package net.marllex.waselak.core.network.di

import io.ktor.client.engine.HttpClientEngineFactory

expect fun httpEngineFactory(): HttpClientEngineFactory<*>
