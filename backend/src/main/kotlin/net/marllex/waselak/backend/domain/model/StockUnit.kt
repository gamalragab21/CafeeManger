package net.marllex.waselak.backend.domain.model

/**
 * Simplified 6-unit system for inventory management.
 * Stock creation uses: KILOGRAM, LITER, PIECE, PACK
 * Recipe ingredients use: GRAM, MILLILITER (+ the stock unit for flexibility)
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

    // Count (self-based)
    PIECE(baseUnit = null, toBaseRate = 1.0, category = UnitCategory.COUNT),

    // Package (self-based)
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
