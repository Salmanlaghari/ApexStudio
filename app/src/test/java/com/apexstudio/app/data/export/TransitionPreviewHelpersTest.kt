package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransitionPreviewHelpersTest {
    private fun clip(id: String, start: Long, end: Long) = MediaClip(
        id = id, name = id, uri = "file:///$id.mp4", durationMs = 20_000L,
        trimStartMs = start, trimEndMs = end
    )

    @Test
    fun `bounded sample uses outgoing tail and incoming head while preserving source bounds`() {
        val sample = buildBoundedTransitionPreviewSample(
            clip("a", 2_000L, 8_000L), clip("b", 10_000L, 18_000L), "wipe_left", 1_500L
        )!!
        assertEquals(1_500L, sample.transitionDurationMs)
        assertEquals(5_000L, sample.plan.entries[0].sourceStartMs)
        assertEquals(8_000L, sample.plan.entries[0].sourceEndMs)
        assertEquals(10_000L, sample.plan.entries[1].sourceStartMs)
        assertEquals(13_000L, sample.plan.entries[1].sourceEndMs)
        assertEquals(1_500L, sample.plan.transitions.single().durationMs)
        assertEquals("wipe_left", sample.plan.transitions.single().transition.type)
        assertEquals(sample.transitionWindowStartMs, sample.plan.transitions.single().startMs)
        assertEquals(sample.transitionWindowEndMs, sample.plan.transitions.single().endMs)
    }

    @Test
    fun `bounded sample clamps requested duration to valid half windows`() {
        val sample = buildBoundedTransitionPreviewSample(
            clip("a", 100L, 900L), clip("b", 2_000L, 2_600L), "fade_black", 1_000L
        )!!
        assertEquals(300L, sample.transitionDurationMs)
        assertEquals(300L, sample.plan.transitions.single().durationMs)
    }

    @Test
    fun `unsupported shader type has no actual-preview sample`() {
        assertNull(
            buildBoundedTransitionPreviewSample(
                clip("a", 0L, 2_000L), clip("b", 0L, 2_000L), "not-supported", 500L
            )
        )
    }

    @Test
    fun `eligibility selects concrete blocker and normal path is eligible`() {
        val a = clip("a", 0L, 2_000L)
        val b = clip("b", 0L, 2_000L)
        val transition = ClipTransition(id = "a-b", fromClipId = "a", toClipId = "b", durationMs = 400L)
        val plan = ProjectTransitionTimeline.build(listOf(a, b), listOf(transition))
        assertNull(transitionPreviewBlocker(plan, true, false, false, null, "", false, false, false))
        assertEquals(
            TransitionPreviewBlocker.BEFORE_AFTER_COMPARE,
            transitionPreviewBlocker(plan, true, true, false, null, "", false, false, false)
        )
        assertEquals(
            TransitionPreviewBlocker.UNSUPPORTED_TRANSITION_TYPE,
            transitionPreviewBlocker(
                plan.copy(transitions = plan.transitions.map { it.copy(transition = transition.copy(type = "not-supported")) }),
                true, false, false, null, "", false, false, false
            )
        )
    }

    @Test
    fun `all other preview blockers report their exact category`() {
        val a = clip("a", 0L, 2_000L)
        val b = clip("b", 0L, 2_000L)
        val transition = ClipTransition(id = "a-b", fromClipId = "a", toClipId = "b", durationMs = 400L)
        val plan = ProjectTransitionTimeline.build(listOf(a, b), listOf(transition))

        assertEquals(
            TransitionPreviewBlocker.NON_VIDEO_MAIN_TRACK,
            transitionPreviewBlocker(
                plan.copy(entries = plan.entries.mapIndexed { index, entry ->
                    if (index == 0) entry.copy(clip = entry.clip.copy(type = ClipType.IMAGE)) else entry
                }),
                true, false, false, null, "", false, false, false
            )
        )
        assertEquals(
            TransitionPreviewBlocker.NON_REPRESENTABLE_SPEED_OR_FILTER,
            transitionPreviewBlocker(plan, false, false, false, null, "", false, false, false)
        )
        assertEquals(
            TransitionPreviewBlocker.AR_FILTER,
            transitionPreviewBlocker(plan, true, false, false, "ar_filter", "", false, false, false)
        )
        assertEquals(
            TransitionPreviewBlocker.COVER_TITLE,
            transitionPreviewBlocker(plan, true, false, false, null, "Cover title", false, false, false)
        )
        assertEquals(
            TransitionPreviewBlocker.GLOBAL_OVERLAYS,
            transitionPreviewBlocker(plan, true, false, false, null, "", false, true, false)
        )
    }
}
