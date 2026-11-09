package com.apexstudio.app.ui.components

import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.screens.editor.editorPreviewLayoutWeight
import com.apexstudio.app.ui.screens.editor.timelineTrackLayoutWeight
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the PR169/170 portrait-forward balance and rendered timeline geometry. */
class RestoreHistoricalTimelineTest {
    private fun clip(id: String, type: ClipType = ClipType.VIDEO, trackIndex: Int = 0) =
        MediaClip(
            id = id,
            name = id,
            uri = "file:///test/$id.mp4",
            durationMs = 30_000L,
            trimEndMs = 30_000L,
            type = type,
            trackIndex = trackIndex,
        )

    @Test
    fun normalModeKeepsThePreviewProminentAndTimelineAvailable() {
        assertEquals(1.15f, editorPreviewLayoutWeight(isTimelineFocused = false), 0f)
        assertEquals(1.2f, timelineTrackLayoutWeight(isTimelineFocused = false), 0f)
        assertEquals(0.001f, editorPreviewLayoutWeight(isTimelineFocused = true), 0f)
        assertEquals(3f, timelineTrackLayoutWeight(isTimelineFocused = true), 0f)
    }

    @Test
    fun timelineKeepsHistoricalRulerV1AndSupportingLayerHeights() {
        assertEquals(40, TIMELINE_HEADER_HEIGHT_DP)
        assertEquals(28, TIMELINE_RULER_HEIGHT_DP)
        assertEquals(60, OVERLAY_LAYER_HEIGHT_DP)
        assertEquals(52, OVERLAY_FILMSTRIP_HEIGHT_DP)
        assertEquals(42, OVERLAY_CLIP_HEIGHT_DP)
        assertEquals(46, AUDIO_LANE_ROW_HEIGHT_DP)
        assertEquals(40, AUDIO_TRACK_ITEM_HEIGHT_DP)
        assertEquals(52.dp, mainVideoLaneHeight(720f, hasMainVideo = true))
        assertEquals(0.dp, mainVideoLaneHeight(720f, hasMainVideo = false))

        val empty = EditorState()
        val main = EditorState(
            project = Project(id = "p", name = "Project", durationMs = 30_000L, clips = listOf(clip("main"))),
        )
        val withOverlay = EditorState(
            project = Project(
                id = "p",
                name = "Project",
                durationMs = 30_000L,
                clips = listOf(clip("main"), clip("overlay", ClipType.OVERLAY, trackIndex = 1)),
            ),
        )
        assertEquals(68.dp, compactTimelineHeight(empty))
        assertEquals(130.dp, compactTimelineHeight(main))
        assertEquals(210.dp, compactTimelineHeight(withOverlay))
    }
}
