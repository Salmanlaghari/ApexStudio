package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCompositionTimelineTest {
    private fun video(
        id: String,
        trimStart: Long = 0L,
        trimEnd: Long = 1_000L,
        speed: Float = 1f,
        track: Int = 0
    ) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = 10_000L,
        trimStartMs = trimStart,
        trimEndMs = trimEnd,
        speedMultiplier = speed,
        trackIndex = track
    )

    @Test
    fun `main video clips concatenate in list order and mapping uses retained trim`() {
        val first = video("first", trimStart = 1_000L, trimEnd = 3_000L)
        val second = video("second", trimStart = 4_000L, trimEnd = 5_500L, speed = 2f)
        val overlay = video("overlay", track = 1)

        val plan = ProjectCompositionTimeline.build(listOf(first, overlay, second))

        assertEquals(2_750L, plan.durationMs)
        assertEquals(listOf("first", "second"), plan.entries.map { it.clip.id })
        assertEquals(0L, plan.entries[0].timelineStartMs)
        assertEquals(2_000L, plan.entries[1].timelineStartMs)
        assertSame(first, plan.entryAt(1_999L)?.clip)
        assertSame(second, plan.entryAt(2_000L)?.clip)
        assertEquals(4_500L, plan.sourceTimeAt(2_250L))
        assertNull(plan.sourceTimeAt(-1L))
        assertNull(plan.sourceTimeAt(plan.durationMs))
    }

    @Test
    fun `variable speed curve maps output elapsed to source elapsed`() {
        val clip = video("ramp", trimStart = 2_000L, trimEnd = 6_000L).copy(
            speedPoints = SpeedCurveMath.flat(2f)
        )
        val plan = ProjectCompositionTimeline.build(listOf(clip))

        assertEquals(2_000L, plan.durationMs)
        assertEquals(2_000L, plan.sourceTimeAt(0L))
        assertEquals(4_000.0, plan.sourceTimeAt(1_000L)!!.toDouble(), 2.0)
        assertEquals(6_000L, plan.entries.single().sourceTimeAtOutputOffset(2_000L))
    }

    @Test
    fun `image occupies its retained still duration and does not advance its source time`() {
        val photo = video("photo", trimStart = 250L, trimEnd = 1_250L).copy(type = ClipType.IMAGE)
        val following = video("following", trimEnd = 500L)
        val plan = ProjectCompositionTimeline.build(listOf(photo, following))

        assertEquals(1_500L, plan.durationMs)
        assertEquals(1_000L, plan.entries.first().durationMs)
        assertEquals(250L, plan.sourceTimeAt(999L))
        assertEquals(1_000L, plan.entries.last().timelineStartMs)
    }

    @Test
    fun `image curve duration matches canonical editor without legacy constant speed`() {
        val photo = video("photo", trimStart = 500L, trimEnd = 2_500L, speed = 4f).copy(
            type = ClipType.IMAGE, speedPoints = SpeedCurveMath.flat(2f)
        )
        val plan = ProjectCompositionTimeline.build(listOf(photo))
        assertEquals(photo.effectiveDurationMs(), plan.durationMs)
        assertEquals(1_000L, plan.durationMs)
        assertEquals(500L, plan.sourceTimeAt(500L))
    }

    @Test
    fun `invalid main source trims are reported while overlay clips are ignored`() {
        val invalid = video("invalid", trimStart = 4_000L, trimEnd = 3_000L)
        val overlay = video("overlay", track = 2)
        val valid = video("valid", trimEnd = 500L)

        val plan = ProjectCompositionTimeline.build(listOf(invalid, overlay, valid))

        assertEquals(listOf("invalid"), plan.excludedClipIds)
        assertEquals(listOf("valid"), plan.entries.map { it.clip.id })
        assertEquals(500L, plan.durationMs)
        assertFalse(plan.entries.any { it.clip.id == "overlay" })
        assertTrue(plan.entryAt(500L) == null)
    }
}
