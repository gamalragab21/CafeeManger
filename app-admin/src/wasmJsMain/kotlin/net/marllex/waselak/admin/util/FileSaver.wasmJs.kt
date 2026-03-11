package net.marllex.waselak.admin.util

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement

actual object FileSaver {
    actual fun saveCsv(content: String, suggestedFileName: String): Boolean {
        return try {
            // Use data URI approach for wasmJs compatibility (avoids Blob JsArray issue)
            val encoded = encodeURIComponent(content)
            val a = document.createElement("a") as HTMLAnchorElement
            a.href = "data:text/csv;charset=utf-8,$encoded"
            a.download = suggestedFileName
            document.body?.appendChild(a)
            a.click()
            document.body?.removeChild(a)
            true
        } catch (e: Exception) {
            false
        }
    }
}

// Use Kotlin/Wasm external fun for encodeURIComponent
private fun encodeURIComponent(str: String): String {
    // Simple percent-encoding for CSV content
    val sb = StringBuilder()
    for (c in str) {
        when {
            c.isLetterOrDigit() || c in "-_.!~*'()" -> sb.append(c)
            else -> {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    sb.append('%')
                    sb.append(((b.toInt() and 0xFF) shr 4).digitToChar(16).uppercaseChar())
                    sb.append((b.toInt() and 0x0F).digitToChar(16).uppercaseChar())
                }
            }
        }
    }
    return sb.toString()
}
