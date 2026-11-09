package com.apexstudio.app.domain.model.timeline

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.OverlayBlend
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayBlendModeTest {
    @Test
    fun softLightKeepsStableShaderIdAndLabel() {
        assertEquals(4, OverlayBlend.SOFT_LIGHT.glId)
        assertEquals("Soft Light", OverlayBlend.SOFT_LIGHT.label)
    }

    @Test
    fun softLightSurvivesLegacyAndTypedOverlayProjection() {
        val legacyClip = MediaClip(
            id = "overlay", name = "Overlay", uri = "file:///overlay.mp4", durationMs = 1_000L,
            trimEndMs = 1_000L, type = ClipType.OVERLAY, trackIndex = 1,
            overlayBlend = OverlayBlend.SOFT_LIGHT
        )
        val imported = Project("project", "Project", 1_000L, clips = listOf(legacyClip)).syncSharedTimeline()
        val typedTrack = imported.timeline.tracks.filterIsInstance<OverlayTrack>().single()
        val typedOverlay = typedTrack.items.single() as OverlayItem.Video

        assertEquals(BlendMode.SOFT_LIGHT, typedOverlay.overlayBlend)

        val typedEdit = imported.copy(timeline = Timeline(listOf(
            typedTrack.copy(items = listOf(typedOverlay.copy(overlayBlend = BlendMode.SOFT_LIGHT)))
        ))).syncSharedTimeline()

        assertEquals(OverlayBlend.SOFT_LIGHT, typedEdit.clips.single().overlayBlend)
    }
}
