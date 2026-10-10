package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.apexstudio.app.data.text.TextFontRegistry

/**
 * Compose [FontFamily] backed by the same bundled OFL fonts the
 * export renderer uses ([TextFontRegistry]), so the live preview
 * shows exactly the typeface that bakes into the MP4.
 */
@Composable
fun rememberTextFontFamily(
    fontKey: String?,
    isBold: Boolean,
    isItalic: Boolean
): FontFamily {
    val context = LocalContext.current
    return remember(fontKey, isBold, isItalic) {
        TextFontRegistry.init(context)
        val typeface = TextFontRegistry.resolve(fontKey, isBold, isItalic)
        FontFamily(typeface)
    }
}

/** Font picker entries shown in the Text panel (key → label). */
val TEXT_FONT_OPTIONS: List<Pair<String, String>> =
    TextFontRegistry.fonts.map { it.key to it.label }

/**
 * Phase 4: fonts grouped by category for the picker's sectioned grid
 * (category → list of key/label pairs).
 */
val TEXT_FONT_GROUPS: List<Pair<String, List<Pair<String, String>>>>
    get() {
        val order = listOf("System", "Display", "Handwriting", "Serif", "Sans Serif")
        val grouped = TextFontRegistry.fonts.groupBy { it.category }
        val ordered = order.filter { grouped.containsKey(it) }
            .map { it to grouped.getValue(it).map { f -> f.key to f.label } }
        val rest = (grouped.keys - order.toSet()).sorted()
            .map { it to grouped.getValue(it).map { f -> f.key to f.label } }
        return ordered + rest
    }
