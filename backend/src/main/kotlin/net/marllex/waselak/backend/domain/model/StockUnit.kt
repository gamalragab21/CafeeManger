package net.marllex.waselak.backend.domain.model

/**
 * Professional unit system for inventory management.
 * Each unit belongs to a category and has a base unit with a conversion rate.
 * Package units are each their own base (no cross-conversion).
 */
enum class StockUnit(
    val baseUnit: StockUnit? = null,
    val toBaseRate: Double = 1.0,
    val category: UnitCategory,
) {
    // Weight (base: GRAM)
    GRAM(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.WEIGHT),
    KILOGRAM(baseUnit = GRAM, toBaseRate = 1000.0, category = UnitCategory.WEIGHT),

    // Volume (base: MILLILITER)
    MILLILITER(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.VOLUME),
    LITER(baseUnit = MILLILITER, toBaseRate = 1000.0, category = UnitCategory.VOLUME),

    // Count (base: PIECE)
    PIECE(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.COUNT),
    DOZEN(baseUnit = PIECE, toBaseRate = 12.0, category = UnitCategory.COUNT),

    // Package (each is its own base — no cross-conversion)
    BOX(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE),
    BAG(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE),
    BOTTLE(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE),
    CAN(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE),
    PACK(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.PACKAGE),
    ;

    val isBaseUnit: Boolean get() = baseUnit == null
    val resolvedBaseUnit: StockUnit get() = baseUnit ?: this

    companion object {
        /** Convert a value from [from] unit to its base unit. */
        fun convertToBase(value: Double, from: StockUnit): Double {
            return value * from.toBaseRate
        }

        /** Convert a value from base unit to [to] unit. */
        fun convertFromBase(value: Double, to: StockUnit): Double {
            return value / to.toBaseRate
        }

        /** Convert a value from [from] unit to [to] unit. */
        fun convert(value: Double, from: StockUnit, to: StockUnit): Double {
            if (from == to) return value
            require(areCompatible(from, to)) {
                "Cannot convert between $from and $to (incompatible unit categories)"
            }
            val inBase = convertToBase(value, from)
            return convertFromBase(inBase, to)
        }

        /** Check if two units can be converted between each other. */
        fun areCompatible(a: StockUnit, b: StockUnit): Boolean {
            if (a == b) return true
            // Package units are only compatible with themselves
            if (a.category == UnitCategory.PACKAGE || b.category == UnitCategory.PACKAGE) {
                return a == b
            }
            return a.resolvedBaseUnit == b.resolvedBaseUnit
        }

        /** Safe parse: returns null if the string is not a valid unit. */
        fun fromString(value: String): StockUnit? {
            return try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class UnitCategory {
    WEIGHT, VOLUME, COUNT, PACKAGE
}
