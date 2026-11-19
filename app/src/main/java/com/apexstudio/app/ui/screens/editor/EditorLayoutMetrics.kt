package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.unit.dp

/** Shared dimensions for the ApexStudio-branded editor chrome. */
internal object EditorLayoutMetrics {
    val TopBarHeight = 52.dp
    val TopBarHorizontalPadding = 10.dp
    /** 24dp timecode space above a comfortable 44dp transport-control row. */
    val PlaybackBarHeight = 68.dp
    val TimelineActionHeight = 48.dp
    /** The compact layer controls remain 44dp tall and comfortably tappable. */
    val TimelineLayerControlsHeight = 44.dp
    /** 36dp of viewport reclaimed from the removed metadata/zoom toolbar. */
    val TimelineExtraVideoLayerHeight = 36.dp
    /** Keep the previous 80dp pane budget: controls + additional video-layer viewport. */
    val TimelineHostExtraHeight = TimelineLayerControlsHeight + TimelineExtraVideoLayerHeight
    /** 46dp reference toolbar row plus its 4dp top/bottom inset. */
    val ReferenceToolbarHeight = 54.dp
    val PreviewCornerRadius = 14.dp
    val PlaybackCornerRadius = 12.dp
}
