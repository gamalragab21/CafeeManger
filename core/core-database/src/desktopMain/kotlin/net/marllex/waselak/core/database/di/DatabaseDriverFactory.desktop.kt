package net.marllex.waselak.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.marllex.waselak.core.database.WaselakDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbPath = File(System.getProperty("user.home"), ".waselak")
        dbPath.mkdirs()
        val dbFile = File(dbPath, "waselak.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (!dbFile.exists()) {
            WaselakDatabase.Schema.create(driver)
        }
        return driver
    }
}
