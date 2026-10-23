package com.apexstudio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Compatibility wrapper; inherit the app color scheme instead of replacing editor-wide Material colors. */
@Composable
fun EditorLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = ApexTypography, shapes = ApexShapes, content = content)
}
