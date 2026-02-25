package net.marllex.waselak.core.model

/**
 * Simplified 6-unit system for inventory management.
 * Stock creation uses: KILOGRAM, LITER, PIECE, PACK
 * Recipe ingredients use: GRAM, MILLILITER (+ the stock unit for flexibility)
 */
enum class StockUnit(
    val baseUnit: StockUnit? = null,
    val toBaseRate: Double = 1.0,
    val category: UnitCategory,
    val displayName: String,
    val displayNameAr: String,
) {
    // Weight (base: GRAM)
    GRAM(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.WEIGHT, displayName = "g", displayNameAr = "جم"),
    KILOGRAM(baseUnit = GRAM, toBaseRate = 1000.0, category = UnitCategory.WEIGHT, displayName = "kg", displayNameAr = "كجم"),

    // Volume (base: MILLILITER)
    MILLILITER(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.VOLUME, displayName = "ml", displayNameAr = "مل"),
    LITER(baseUnit = MILLILITER, toBaseRate = 1000.0, category = UnitCategory.VOLUME, displayName = "L", displayNameAr = "لتر"),

    // Count (self-based)
    PIECE(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.COUNT, displayName = "pcs", displayNameAr = "قطعة"),

    // Package (self-based)
    PACK(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "pack", displayNameAr = "عبوة"),
    ;

    val isBaseUnit: Boolean get() = baseUnit == null
    val resolvedBaseUnit: StockUnit get() = baseUnit ?: this

    companion object {
        fun fromString(value: String): StockUnit? {
            return try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        fun convert(value: Double, from: StockUnit, to: StockUnit): Double {
            if (from == to) return value
            if (!areCompatible(from, to)) return value
            val inBase = value * from.toBaseRate
            return inBase / to.toBaseRate
        }

        fun areCompatible(a: StockUnit, b: StockUnit): Boolean {
            if (a == b) return true
            if (a.category == UnitCategory.PACKAGE || b.category == UnitCategory.PACKAGE) return a == b
            return a.resolvedBaseUnit == b.resolvedBaseUnit
        }

        /** All units grouped by category for UI selectors. */
        val byCategory: Map<UnitCategory, List<StockUnit>>
            get() = entries.groupBy { it.category }
    }
}

enum class UnitCategory {
    WEIGHT, VOLUME, COUNT, PACKAGE
}
