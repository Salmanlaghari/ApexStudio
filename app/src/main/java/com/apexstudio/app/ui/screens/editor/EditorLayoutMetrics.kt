package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.unit.dp

/** Shared dimensions for the ApexStudio-branded editor chrome. */
internal object EditorLayoutMetrics {
    val TopBarHeight = 52.dp
    val TopBarHorizontalPadding = 10.dp
    /** 24dp timecode space above a comfortable 44dp transport-control row. */
    val PlaybackBarHeight = 68.dp
    val TimelineActionHeight = 48.dp
    /** Single-line metadata header, one 44dp touch-target row, gap and insets. */
    val TimelineToolbarHeight = 80.dp
    /** 46dp reference toolbar row plus its 4dp top/bottom inset. */
    val ReferenceToolbarHeight = 54.dp
    val PreviewCornerRadius = 14.dp
    val PlaybackCornerRadius = 12.dp
}
