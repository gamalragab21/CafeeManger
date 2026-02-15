package net.marllex.waselak.core.auth.di

import android.content.Context
import org.koin.mp.KoinPlatform

actual fun dataStorePath(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return context.filesDir.resolve(AUTH_DATASTORE_FILE).absolutePath
}
