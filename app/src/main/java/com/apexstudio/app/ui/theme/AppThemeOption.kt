package com.apexstudio.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Complete app-chrome color tokens for one selectable UI theme. */
@Immutable
data class ApexThemePalette(
    val bgDeep: Color,
    val bgBase: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val primary: Color,
    val primaryGlow: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val divider: Color,
    val border: Color
)

/** Full dark app themes. Timeline tracks and status colors stay semantic across every option. */
enum class ApexThemeOption(
    val storedValue: String,
    val displayName: String,
    val palette: ApexThemePalette
) {
    NEON(
        storedValue = "neon",
        displayName = "Neon Dark",
        palette = ApexThemePalette(
            bgDeep = Color(0xFF080A0F),
            bgBase = Color(0xFF0B0E14),
            bgSurface = Color(0xFF10141E),
            bgElevated = Color(0xFF1A2236),
            primary = Color(0xFF00E5FF),
            primaryGlow = Color(0xFF00B8D4),
            secondary = Color(0xFF7C4DFF),
            onSecondary = Color(0xFFFFFFFF),
            secondaryGlow = Color(0xFF5E35B1),
            textPrimary = Color(0xFFF1F5F9),
            textSecondary = Color(0xFF94A3B8),
            textTertiary = Color(0xFF64748B),
            textMuted = Color(0xFF475569),
            divider = Color(0xFF1F2937),
            border = Color(0xFF1E293B)
        )
    ),
    VIOLET(
        storedValue = "violet",
        displayName = "Violet Night",
        palette = ApexThemePalette(
            bgDeep = Color(0xFF080712),
            bgBase = Color(0xFF100D1A),
            bgSurface = Color(0xFF171326),
            bgElevated = Color(0xFF241C35),
            primary = Color(0xFFCE93D8),
            primaryGlow = Color(0xFFBA68C8),
            secondary = Color(0xFF9575CD),
            onSecondary = Color(0xFF080712),
            secondaryGlow = Color(0xFF7E57C2),
            textPrimary = Color(0xFFF4EFF8),
            textSecondary = Color(0xFFB3A8BE),
            textTertiary = Color(0xFF80758D),
            textMuted = Color(0xFF635A70),
            divider = Color(0xFF31283D),
            border = Color(0xFF3B3048)
        )
    ),
    EMBER(
        storedValue = "ember",
        displayName = "Ember Night",
        palette = ApexThemePalette(
            bgDeep = Color(0xFF100806),
            bgBase = Color(0xFF170C09),
            bgSurface = Color(0xFF21130F),
            bgElevated = Color(0xFF332019),
            primary = Color(0xFFFFB74D),
            primaryGlow = Color(0xFFF57C00),
            secondary = Color(0xFFFF7043),
            onSecondary = Color(0xFF100806),
            secondaryGlow = Color(0xFFE64A19),
            textPrimary = Color(0xFFFFF2E8),
            textSecondary = Color(0xFFC6A99B),
            textTertiary = Color(0xFF98796E),
            textMuted = Color(0xFF72584F),
            divider = Color(0xFF3A2822),
            border = Color(0xFF4A332B)
        )
    );

    val primary: Color get() = palette.primary
    val secondary: Color get() = palette.secondary

    companion object {
        val default: ApexThemeOption = NEON

        fun fromStoredValue(value: String?): ApexThemeOption =
            entries.firstOrNull { it.storedValue == value } ?: default
    }
}
