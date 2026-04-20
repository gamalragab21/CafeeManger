package net.marllex.waselak.core.database.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.marllex.waselak.core.database.WaselakDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        // WAL + relaxed sync are applied via the open-helper Callback so that the PRAGMAs
        // are issued with rawQuery — journal_mode returns a result row, which execSQL
        // (what AndroidSqliteDriver.execute delegates to) would reject.
        val callback = object : AndroidSqliteDriver.Callback(WaselakDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                runCatching { db.query("PRAGMA journal_mode = WAL;").close() }
                runCatching { db.query("PRAGMA synchronous = NORMAL;").close() }
                runCatching { db.query("PRAGMA temp_store = MEMORY;").close() }
            }
        }
        return AndroidSqliteDriver(
            schema = WaselakDatabase.Schema,
            context = context,
            name = "waselak.db",
            callback = callback,
        ).also { it.applyPerformanceIndexes() }
    }
}
