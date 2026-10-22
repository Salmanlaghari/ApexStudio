package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The timeline starts immediately below transport and consumes the available
 * space above the lower tool dock. Row heights remain content-sized inside its
 * scroll viewport; extra space never creates decorative or empty material lanes.
 */
@Composable
internal fun ColumnScope.CenteredTimelineWorkspace(
    layoutWeight: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().weight(layoutWeight),
        content = content
    )
}
