package com.apexstudio.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Named app themes. All retain the dark editor surfaces while changing the app accent. */
enum class ApexThemeOption(
    val storedValue: String,
    val displayName: String,
    val primary: Color,
    val secondary: Color
) {
    NEON(
        storedValue = "neon",
        displayName = "Neon Dark",
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFF7C4DFF)
    ),
    VIOLET(
        storedValue = "violet",
        displayName = "Violet Night",
        primary = Color(0xFFCE93D8),
        secondary = Color(0xFF9575CD)
    ),
    EMBER(
        storedValue = "ember",
        displayName = "Ember Night",
        primary = Color(0xFFFFB74D),
        secondary = Color(0xFFFF7043)
    );

    companion object {
        val default: ApexThemeOption = NEON

        fun fromStoredValue(value: String?): ApexThemeOption =
            entries.firstOrNull { it.storedValue == value } ?: default
    }
}
