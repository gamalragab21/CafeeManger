package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.MutableState
import java.util.Locale
import java.util.prefs.Preferences

private const val PREF_KEY_LANGUAGE = "waselak_language"

private val prefs: Preferences = Preferences.userNodeForPackage(LanguageSelectorDesktop::class.java)

/** Marker class for Preferences node. */
private class LanguageSelectorDesktop

// Backup persistence: a plain text file under ~/.waselak/. JVM Preferences
// on macOS can lose unflushed writes if the app is force-quit (the backing
// CFPreferences plist syncs lazily and Compose Desktop's exit path doesn't
// always wait for it). The file write is synchronous so it survives even
// a hard kill. We use whichever source has the most recent value at load
// time, and write both on every change.
private val backupFile: java.io.File by lazy {
    val home = System.getProperty("user.home") ?: "."
    val dir = java.io.File(home, ".waselak")
    if (!dir.exists()) dir.mkdirs()
    java.io.File(dir, "language")
}

private fun readPersisted(): String {
    val fileVal = runCatching { backupFile.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }
    val prefVal = prefs.get(PREF_KEY_LANGUAGE, null)
    return fileVal ?: prefVal ?: Locale.getDefault().language
}

private fun writePersisted(code: String) {
    // Write both for redundancy. Prefs are nice for system integration
    // (System Settings → Java); the file is the source of truth for the
    // next app launch because it can't be lost to lazy plist flushes.
    runCatching {
        prefs.put(PREF_KEY_LANGUAGE, code)
        prefs.flush()
    }
    runCatching { backupFile.writeText(code) }
}

// Flush prefs on JVM exit too — belt-and-braces for the case where
// applyLanguage() ran but the backing store hadn't synced yet.
// File-level Kotlin can't have an `init {}` block, so we register the
// hook eagerly via a property that runs once when this file's classfile
// is loaded.
private val shutdownHookRegistered: Boolean = run {
    runCatching {
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { prefs.flush() }
        })
    }
    true
}

/** Global observable language state — app root should observe this */
val currentLanguageState: MutableState<String> = mutableStateOf(readPersisted())

/** Read persisted language or fall back to system default. */
fun getPersistedLanguage(): String = readPersisted()

/** Apply and persist a language code, triggers recomposition via currentLanguageState. */
fun applyLanguage(code: String) {
    writePersisted(code)
    Locale.setDefault(Locale(code))
    currentLanguageState.value = code
}

@Composable
actual fun LanguageSelector(modifier: Modifier) {
    var selectedLang by remember { mutableStateOf(getPersistedLanguage()) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Language / اللغة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        listOf("en" to "English", "ar" to "العربية").forEach { (code, name) ->
            val isSelected = selectedLang == code
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        selectedLang = code
                        applyLanguage(code)
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
