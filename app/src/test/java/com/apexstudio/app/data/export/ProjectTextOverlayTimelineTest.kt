package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.data.text.TextAnimEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTextOverlayTimelineTest {
    private fun clip(id: String, durationMs: Long, track: Int = 0, type: ClipType = ClipType.VIDEO) =
        MediaClip(
            id = id,
            name = id,
            uri = "file:///$id.mp4",
            durationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            trackIndex = track,
            type = type
        )

    @Test
    fun `project range is segmented as clip-local windows across V1 boundaries`() {
        val clips = listOf(clip("a", 1_000L), clip("non-v1", 5_000L, track = 1), clip("b", 700L))
        val template = TextOverlay.of(id = "title", text = "Across", x = 0.2f, y = 0.4f).copy(
            colorArgb = 0xFF123456,
            bgArgb = 0xAA010203,
            animationType = "FADE_IN",
            rotationDeg = 13f,
            startMs = 0L,
            endMs = Long.MAX_VALUE
        )

        val pieces = ProjectTextOverlayTimeline.segments(clips, template, 750L, 1_400L)

        assertEquals(listOf("a", "b"), pieces.map { it.clipId })
        assertEquals(750L, pieces[0].overlay.startMs)
        assertEquals(1_000L, pieces[0].overlay.endMs)
        assertEquals(750L, pieces[0].overlay.animationWindowStartMs)
        assertEquals(1_400L, pieces[0].overlay.animationWindowEndMs)
        assertEquals(0L, pieces[1].overlay.startMs)
        assertEquals(400L, pieces[1].overlay.endMs)
        assertEquals(-250L, pieces[1].overlay.animationWindowStartMs)
        assertEquals(400L, pieces[1].overlay.animationWindowEndMs)
        assertTrue(pieces.all { it.overlay.projectTextLayerId != null })
        assertEquals(1, pieces.map { it.overlay.projectTextLayerId }.distinct().size)
        pieces.forEach { piece ->
            assertEquals("title", piece.overlay.id)
            assertEquals("Across", piece.overlay.text)
            assertEquals(template.colorArgb, piece.overlay.colorArgb)
            assertEquals(template.bgArgb, piece.overlay.bgArgb)
            assertEquals(template.animationType, piece.overlay.animationType)
            assertEquals(template.rotationDeg, piece.overlay.rotationDeg)
        }
    }

    @Test
    fun `range reconstruction clamps legacy max end and combines existing segments`() {
        val first = clip("a", 1_000L).copy(
            textOverlays = listOf(TextOverlay.of(id = "legacy", startMs = 200L, endMs = Long.MAX_VALUE))
        )
        val second = clip("b", 600L).copy(
            textOverlays = listOf(TextOverlay.of(id = "segmented", startMs = 0L, endMs = 250L))
        )
        val third = clip("c", 300L).copy(
            textOverlays = listOf(TextOverlay.of(id = "segmented", startMs = 0L, endMs = 100L))
        )
        val clips = listOf(first, second, third)

        assertEquals(ProjectTextOverlayTimeline.Range(200L, 1_000L), ProjectTextOverlayTimeline.range(clips, "legacy"))
        // Same-ID legacy records are independent unless an explicit group links them.
        assertEquals(ProjectTextOverlayTimeline.Range(1_000L, 1_250L), ProjectTextOverlayTimeline.range(clips, "segmented"))
        assertNull(ProjectTextOverlayTimeline.range(clips, "missing"))
    }

    @Test
    fun `invalid or out of composition ranges are rejected or clamped without creating empty segments`() {
        val clips = listOf(clip("a", 500L), clip("b", 500L))
        assertTrue(ProjectTextOverlayTimeline.segments(clips, TextOverlay.of(id = "t"), -1L, 100L).isEmpty())
        assertTrue(ProjectTextOverlayTimeline.segments(clips, TextOverlay.of(id = "t"), 100L, 100L).isEmpty())
        assertEquals(
            listOf(ProjectTextOverlayTimeline.Segment("b", TextOverlay.of(id = "t").copy(
                startMs = 450L,
                endMs = 500L,
                animationWindowStartMs = 450L,
                animationWindowEndMs = 500L,
                projectTextLayerId = "expected-group",
                rangeEndExclusive = true
            ))),
            ProjectTextOverlayTimeline.segments(clips, TextOverlay.of(id = "t").copy(projectTextLayerId = "expected-group"), 950L, 9_000L)
        )
    }

    @Test
    fun `image still contributes its output duration while non V1 overlay clips are excluded`() {
        val clips = listOf(clip("still", 250L, type = ClipType.IMAGE), clip("overlay", 1_000L, track = 1))
        val segments = ProjectTextOverlayTimeline.segments(clips, TextOverlay.of(id = "t"), 100L, 300L)
        assertEquals(1, segments.size)
        assertEquals("still", segments.single().clipId)
        assertEquals(100L, segments.single().overlay.startMs)
        assertEquals(250L, segments.single().overlay.endMs)
    }

    @Test
    fun `segmented states match one logical overlay across boundaries`() {
        val clips = listOf(clip("a", 1_000L), clip("b", 1_000L))
        val sampleTimes = listOf(700L, 750L, 900L, 999L, 1_000L, 1_100L, 1_250L, 1_399L)
        val cases = listOf(
            TextOverlay.of(id = "fade", text = "Fade").copy(
                animationType = "FADE_IN", animationDurationMs = 800L,
                outroAnimationType = "FADE_OUT", outroAnimationDurationMs = 600L
            ),
            TextOverlay.of(id = "type", text = "Typewriter").copy(
                animationType = "TYPEWRITER", animationDurationMs = 800L,
                outroAnimationType = "FADE_OUT", outroAnimationDurationMs = 600L
            ),
            TextOverlay.of(id = "pulse", text = "Pulse").copy(
                animationType = "PULSE", outroAnimationType = "FADE_OUT", outroAnimationDurationMs = 600L
            ),
            TextOverlay.of(id = "bounce", text = "Bounce").copy(
                animationType = "BOUNCE", outroAnimationType = "FADE_OUT", outroAnimationDurationMs = 600L
            )
        )

        for (template in cases) {
            val segments = ProjectTextOverlayTimeline.segments(clips, template, 700L, 1_400L)
            val unsplit = template.copy(startMs = 700L, endMs = 1_400L)
            for (projectTime in sampleTimes) {
                val segmentIndex = if (projectTime < 1_000L) 0 else 1
                val segment = segments[segmentIndex].overlay
                val localTime = if (projectTime < 1_000L) projectTime else projectTime - 1_000L
                assertEquals(
                    "${template.animationType} at project time $projectTime",
                    TextAnimEngine.compute(unsplit, projectTime),
                    TextAnimEngine.compute(segment, localTime)
                )
                if (template.animationType == "PULSE" || template.animationType == "BOUNCE") {
                    assertEquals(
                        "${template.animationType} state key at project time $projectTime",
                        TextAnimEngine.stateKey(unsplit, projectTime),
                        TextAnimEngine.stateKey(segment, localTime)
                    )
                }
            }
        }
    }
    @Test
    fun `logical group range uses only explicitly linked segments despite legacy id collision`() {
        val legacy = TextOverlay.of(id = "same", startMs = 100L, endMs = 400L)
        val linked = TextOverlay.of(id = "same", startMs = 0L, endMs = 300L)
            .copy(projectTextLayerId = "logical-group")
        val clips = listOf(
            clip("legacy", 1_000L).copy(textOverlays = listOf(legacy)),
            clip("group-a", 1_000L).copy(textOverlays = listOf(linked.copy(startMs = 200L, endMs = 1_000L))),
            clip("group-b", 1_000L).copy(textOverlays = listOf(linked.copy(startMs = 0L, endMs = 100L)))
        )
        assertEquals(ProjectTextOverlayTimeline.Range(100L, 400L),
            ProjectTextOverlayTimeline.range(clips, "same", "legacy"))
        assertEquals(ProjectTextOverlayTimeline.Range(1_200L, 2_100L),
            ProjectTextOverlayTimeline.range(clips, "same", "group-a"))
        assertEquals(ProjectTextOverlayTimeline.Range(1_200L, 2_100L),
            ProjectTextOverlayTimeline.range(clips, "same"))
    }

    @Test
    fun `legacy text ranges remain end inclusive while new project segments are half open`() {
        val legacy = TextOverlay.of(id = "legacy", startMs = 10L, endMs = 20L)
        assertTrue(legacy.isActiveAt(20L))
        val segment = ProjectTextOverlayTimeline.segments(
            listOf(clip("a", 100L)), legacy, 10L, 20L
        ).single().overlay
        assertTrue(segment.rangeEndExclusive)
        assertTrue(segment.isActiveAt(19L))
        org.junit.Assert.assertFalse(segment.isActiveAt(20L))
    }

}
