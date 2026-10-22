package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Consume the space below transport without inflating compact track heights.
 * The following feature dock stays at the bottom of the inset-safe editor;
 * occupied lanes are centered here and retain their existing scroll viewport
 * when the available space is smaller than their combined content height.
 */
@Composable
internal fun ColumnScope.CenteredTimelineWorkspace(
    contentHeight: Dp,
    isFocused: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().then(
                if (isFocused) Modifier.fillMaxHeight()
                else Modifier.heightIn(max = contentHeight)
            ),
            content = content
        )
    }
}
