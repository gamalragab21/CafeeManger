package net.marllex.waselak.core.model

/**
 * Client-side mirror of the backend StockUnit enum.
 * Professional unit system for inventory management.
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
    CUP(baseUnit = MILLILITER, toBaseRate = 240.0, category = UnitCategory.VOLUME, displayName = "cup", displayNameAr = "كوب"),
    TABLESPOON(baseUnit = MILLILITER, toBaseRate = 15.0, category = UnitCategory.VOLUME, displayName = "tbsp", displayNameAr = "م.ك"),
    TEASPOON(baseUnit = MILLILITER, toBaseRate = 5.0, category = UnitCategory.VOLUME, displayName = "tsp", displayNameAr = "م.ص"),

    // Count (base: PIECE)
    PIECE(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.COUNT, displayName = "pcs", displayNameAr = "قطعة"),
    DOZEN(baseUnit = PIECE, toBaseRate = 12.0, category = UnitCategory.COUNT, displayName = "dozen", displayNameAr = "دزينة"),
    PLATE(baseUnit = PIECE, toBaseRate = 1.0, category = UnitCategory.COUNT, displayName = "plate", displayNameAr = "طبق"),

    // Package (each is its own base)
    BOX(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "box", displayNameAr = "صندوق"),
    BAG(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "bag", displayNameAr = "كيس"),
    BOTTLE(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "bottle", displayNameAr = "زجاجة"),
    CAN(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "can", displayNameAr = "علبة"),
    PACK(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "pack", displayNameAr = "عبوة"),
    CARTON(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "carton", displayNameAr = "كرتونة"),
    SACK(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "sack", displayNameAr = "شوال"),
    TRAY(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "tray", displayNameAr = "صينية"),
    BUCKET(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "bucket", displayNameAr = "جردل"),
    ROLL(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE, displayName = "roll", displayNameAr = "رول"),
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
