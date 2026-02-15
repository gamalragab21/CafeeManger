package net.marllex.waselak.core.database.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.marllex.waselak.core.database.WaselakDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(WaselakDatabase.Schema, context, "waselak.db")
    }
}
