package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val cardShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compactHeight == null) Modifier.weight(layoutWeight) else Modifier.height(compactHeight))
            .clip(cardShape)
            .background(Color(0xFF0D1524))
            .border(1.dp, Color(0xFF1B4664), cardShape)
            .testTag("centered-timeline-workspace"),
        content = content
    )
}
