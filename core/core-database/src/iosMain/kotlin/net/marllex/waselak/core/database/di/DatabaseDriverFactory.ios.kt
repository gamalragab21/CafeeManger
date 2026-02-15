package net.marllex.waselak.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import net.marllex.waselak.core.database.WaselakDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(WaselakDatabase.Schema, "waselak.db")
    }
}
