package net.marllex.waselak.admin.util

/**
 * Represents a column in a CSV export.
 * @param header The column header text
 * @param extract Function to extract the cell value from a data item
 */
data class CsvColumn<T>(
    val header: String,
    val extract: (T) -> String
)

/**
 * Builds a CSV string from a list of items and column definitions.
 * Handles quoting for values containing commas, quotes, or newlines (RFC 4180).
 */
fun <T> buildCsvString(data: List<T>, columns: List<CsvColumn<T>>): String {
    val sb = StringBuilder()

    // BOM for Excel UTF-8 detection
    sb.append('\uFEFF')

    // Header row
    sb.appendLine(columns.joinToString(",") { escapeCsvField(it.header) })

    // Data rows
    for (item in data) {
        sb.appendLine(columns.joinToString(",") { col -> escapeCsvField(col.extract(item)) })
    }

    return sb.toString()
}

private fun escapeCsvField(value: String): String {
    return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}
