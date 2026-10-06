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
