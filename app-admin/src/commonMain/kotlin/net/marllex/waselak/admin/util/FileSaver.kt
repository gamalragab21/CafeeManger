package net.marllex.waselak.admin.util

/**
 * Platform-specific file saving.
 * Desktop: Opens a save dialog via JFileChooser.
 * Android: Shares via share intent.
 */
expect object FileSaver {
    /**
     * Save or share CSV content.
     * @param content The CSV string content
     * @param suggestedFileName Suggested file name (e.g., "vendors_export.csv")
     * @return true if saved/shared successfully
     */
    fun saveCsv(content: String, suggestedFileName: String): Boolean
}
