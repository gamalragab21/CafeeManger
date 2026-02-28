package net.marllex.waselak.core.ui.util

object VariantDisplayHelper {

    /**
     * Parses variant_options_snapshot JSON and returns a human-readable summary.
     * e.g., "Size: Large, Temp: Hot"
     *
     * Uses simple string parsing to avoid kotlinx.serialization dependency.
     * Expected JSON format: [{"groupName":"Size","optionName":"Large",...}, ...] (camelCase)
     * or [{"group_name":"Size","option_name":"Large",...}, ...] (snake_case from backend)
     */
    fun formatVariantSummary(variantOptionsSnapshot: String?): String? {
        if (variantOptionsSnapshot.isNullOrBlank() || variantOptionsSnapshot == "[]") return null
        return try {
            val parts = mutableListOf<String>()
            // Simple regex to extract groupName/group_name and optionName/option_name pairs
            val groupPattern = Regex(""""(?:groupName|group_name)"\s*:\s*"([^"]+)"""")
            val optionPattern = Regex(""""(?:optionName|option_name)"\s*:\s*"([^"]+)"""")

            // Split by object boundaries
            val objects = variantOptionsSnapshot.split("},").map { it.trim() }
            for (obj in objects) {
                val groupMatch = groupPattern.find(obj)
                val optionMatch = optionPattern.find(obj)
                if (groupMatch != null && optionMatch != null) {
                    parts.add("${groupMatch.groupValues[1]}: ${optionMatch.groupValues[1]}")
                }
            }

            if (parts.isEmpty()) null else parts.joinToString(", ")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns variant summary formatted for HTML receipt.
     * e.g., "<br><small style='color:#666'>Size: Large, Temp: Hot</small>"
     */
    fun formatVariantSummaryHtml(variantOptionsSnapshot: String?): String? {
        val summary = formatVariantSummary(variantOptionsSnapshot) ?: return null
        return "<br><small style='color:#666'>$summary</small>"
    }
}
