package net.marllex.waselak.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import net.marllex.waselak.core.database.WaselakDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(WaselakDatabase.Schema, "waselak.db")
        // PRAGMAs — NativeSqliteDriver.execute tolerates pragmas that return rows.
        runCatching { driver.execute(null, "PRAGMA journal_mode = WAL;", 0) }
        runCatching { driver.execute(null, "PRAGMA synchronous = NORMAL;", 0) }
        runCatching { driver.execute(null, "PRAGMA temp_store = MEMORY;", 0) }
        driver.applyPerformanceIndexes()
        return driver
    }
}
