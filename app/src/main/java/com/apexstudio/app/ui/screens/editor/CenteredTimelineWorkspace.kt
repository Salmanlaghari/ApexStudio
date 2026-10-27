package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp

/**
 * Normal phone mode bounds the timeline to its populated rows; focus mode can
 * omit [compactHeight] and use [layoutWeight] to expand the editable viewport.
 */
@Composable
internal fun ColumnScope.CenteredTimelineWorkspace(
    layoutWeight: Float = 1f,
    compactHeight: Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compactHeight == null) Modifier.weight(layoutWeight) else Modifier.height(compactHeight))
            .testTag("centered-timeline-workspace"),
        content = content
    )
}
