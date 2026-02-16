package net.marllex.waselak.core.ui.theme

import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════════════════════
//  Wasel POS — Color Palette
//  Blue Primary + Cyan Accent + Warm Orange Secondary + Clean Slate
// ════════════════════════════════════════════════════════════════════

// ─── Primary: Blue — professional, trustworthy, modern POS ─────────
val Primary = Color(0xFF1565C0)             // Blue 800
val PrimaryLight = Color(0xFF42A5F5)        // Blue 400
val PrimaryDark = Color(0xFF0D47A1)         // Blue 900
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFD1E4FF)    // Blue 50 custom
val OnPrimaryContainer = Color(0xFF001D36)  // Blue 950

// ─── Secondary: Orange — warm, action-oriented, food-related ───────
val Secondary = Color(0xFFE65100)           // Deep Orange 800
val SecondaryLight = Color(0xFFFF8A50)      // Orange 300
val SecondaryDark = Color(0xFFBF360C)       // Deep Orange 900
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFFFE0B2)  // Orange 100
val OnSecondaryContainer = Color(0xFF4E2700)

// ─── Tertiary: Cyan — fresh accent, complementary to blue ──────────
val Tertiary = Color(0xFF00838F)            // Cyan 800
val TertiaryLight = Color(0xFF4DD0E1)       // Cyan 300
val TertiaryDark = Color(0xFF006064)        // Cyan 900
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFB2EBF2)   // Cyan 100
val OnTertiaryContainer = Color(0xFF002022)

// ─── Surfaces — cool, clean slate neutrals ────────────────────────
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF111827)         // Slate 900
val SurfaceVariantLight = Color(0xFFF1F5F9) // Slate 100
val SurfaceVariantDark = Color(0xFF1E293B)  // Slate 800

// ─── Backgrounds ──────────────────────────────────────────────────
val BackgroundLight = Color(0xFFF8FAFC)     // Slate 50
val BackgroundDark = Color(0xFF0F172A)      // Slate 950

// ─── Outline ──────────────────────────────────────────────────────
val OutlineLight = Color(0xFFCBD5E1)        // Slate 300
val OutlineDark = Color(0xFF475569)         // Slate 600
val OutlineVariantLight = Color(0xFFE2E8F0) // Slate 200
val OutlineVariantDark = Color(0xFF334155)  // Slate 700

// ─── Error / Success / Warning ────────────────────────────────────
val Error = Color(0xFFDC2626)               // Red 600
val ErrorLight = Color(0xFFFCA5A5)          // Red 300
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFEE2E2)      // Red 100
val OnErrorContainer = Color(0xFF991B1B)    // Red 800

val Success = Color(0xFF16A34A)             // Green 600
val SuccessLight = Color(0xFF86EFAC)        // Green 300
val OnSuccess = Color(0xFFFFFFFF)
val SuccessContainer = Color(0xFFDCFCE7)    // Green 100

val Warning = Color(0xFFEA580C)             // Orange 600
val WarningLight = Color(0xFFFDBA74)        // Orange 300
val OnWarning = Color(0xFFFFFFFF)
val WarningContainer = Color(0xFFFFF7ED)    // Orange 50

// ─── Order status colors ─────────────────────────────────────────
val StatusCreated = Color(0xFF94A3B8)       // Slate 400
val StatusConfirmed = Color(0xFF2563EB)     // Blue 600
val StatusInPreparation = Color(0xFFF59E0B) // Amber 500
val StatusServed = Color(0xFF22C55E)        // Green 500
val StatusReady = Color(0xFF10B981)         // Emerald 500
val StatusAssigned = Color(0xFF7C3AED)      // Violet 600
val StatusOutForDelivery = Color(0xFF8B5CF6)// Violet 500
val StatusDelivered = Color(0xFF10B981)     // Emerald 500
val StatusOnTable = Color(0xFF2563EB)       // Blue 600
val StatusCompleted = Color(0xFF059669)     // Emerald 600
val StatusCanceled = Color(0xFFEF4444)      // Red 500

// ─── Role colors ──────────────────────────────────────────────────
val ManagerColor = Color(0xFF1565C0)        // Blue 800
val CashierColor = Color(0xFF00838F)        // Cyan 800
val DeliveryColor = Color(0xFFE65100)       // Deep Orange 800

// ─── Channel colors ───────────────────────────────────────────────
val DineInColor = Color(0xFF1565C0)         // Blue
val DeliveryChannelColor = Color(0xFFE65100)// Deep Orange
val TakeawayColor = Color(0xFF2E7D32)       // Green

// ─── Table status colors ──────────────────────────────────────────
val TableAvailable = Color(0xFF10B981)      // Emerald 500
val TableOccupied = Color(0xFFEF4444)       // Red 500
val TableReserved = Color(0xFFF59E0B)       // Amber 500

// ─── Stock status colors ──────────────────────────────────────────
val StockHealthy = Color(0xFF10B981)        // Emerald 500
val StockLow = Color(0xFFF59E0B)            // Amber 500
val StockOut = Color(0xFFEF4444)            // Red 500

// ─── Receipt semantic colors (light) ─────────────────────────────
val ReceiptBackground = Color(0xFFFAFAF9)       // Stone 50
val ReceiptSurface = Color(0xFFFFFFFF)           // White card
val ReceiptTextPrimary = Color(0xFF1C1917)       // Stone 900
val ReceiptTextSecondary = Color(0xFF78716C)     // Stone 400
val ReceiptDivider = Color(0xFFE7E5E4)           // Stone 200
val ReceiptTotalText = Color(0xFF0D9488)         // Teal 600
val ReceiptSectionBg = Color(0xFFF5F5F4)         // Stone 100

// ─── Receipt semantic colors (dark) ──────────────────────────────
val ReceiptBackgroundDark = Color(0xFF1C1917)    // Stone 900
val ReceiptSurfaceDark = Color(0xFF292524)       // Stone 800
val ReceiptTextPrimaryDark = Color(0xFFFAFAF9)  // Stone 50
val ReceiptTextSecondaryDark = Color(0xFFA8A29E) // Stone 400
val ReceiptDividerDark = Color(0xFF44403C)       // Stone 700
val ReceiptTotalTextDark = Color(0xFF2DD4BF)     // Teal 400
val ReceiptSectionBgDark = Color(0xFF292524)     // Stone 800

// ─── Chart accent colors ─────────────────────────────────────────
val ChartBlue = Color(0xFF3B82F6)           // Blue 500
val ChartGreen = Color(0xFF22C55E)          // Green 500
val ChartAmber = Color(0xFFF59E0B)          // Amber 500
val ChartPurple = Color(0xFF8B5CF6)         // Violet 500
val ChartCyan = Color(0xFF06B6D4)           // Cyan 500
val ChartRose = Color(0xFFF43F5E)           // Rose 500
val ChartOrange = Color(0xFFF97316)         // Orange 500
val ChartIndigo = Color(0xFF6366F1)         // Indigo 500

// ─── Surface container colors for dark mode ──────────────────────
val SurfaceContainerLowest = Color(0xFF0C0F1A)
val SurfaceContainerLow = Color(0xFF111827)
val SurfaceContainer = Color(0xFF1A2035)
val SurfaceContainerHigh = Color(0xFF1E293B)
val SurfaceContainerHighest = Color(0xFF243046)
