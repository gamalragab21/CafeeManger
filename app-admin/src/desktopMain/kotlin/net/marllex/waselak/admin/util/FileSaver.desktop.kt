package net.marllex.waselak.admin.util

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual object FileSaver {
    actual fun saveCsv(content: String, suggestedFileName: String): Boolean {
        return try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Save CSV"
                selectedFile = File(suggestedFileName)
                fileFilter = FileNameExtensionFilter("CSV Files (*.csv)", "csv")
            }

            val result = chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                var file = chooser.selectedFile
                if (!file.name.endsWith(".csv", ignoreCase = true)) {
                    file = File(file.absolutePath + ".csv")
                }
                file.writeText(content, Charsets.UTF_8)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
