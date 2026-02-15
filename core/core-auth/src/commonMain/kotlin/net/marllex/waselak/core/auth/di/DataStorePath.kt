package net.marllex.waselak.core.auth.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

const val AUTH_DATASTORE_FILE = "waselak_auth.preferences_pb"

expect fun dataStorePath(): String

fun createDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { dataStorePath().toPath() }
    )
