package net.marllex.waselak.core.auth.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

fun authDataStoreFileName(appName: String): String =
    "waselak_${appName}_auth.preferences_pb"

expect fun dataStorePath(appName: String): String

fun createDataStore(appName: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { dataStorePath(appName).toPath() }
    )
