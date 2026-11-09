package com.apexstudio.app.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTimingModelVersionTest {
    private val legacyJson = """{"id":"legacy","name":"Legacy","durationMs":10000}"""

    @Test
    fun `projects without timing version decode as legacy`() {
        val project = Json.decodeFromString<Project>(legacyJson)
        assertEquals(0, project.timingModelVersion)
        assertTrue(project.timeline.tracks.isEmpty())
    }

    @Test
    fun `legacy non normal main clip with independent placements is flagged without retiming`() {
        val main = MediaClip(
            id = "main", name = "main.mp4", uri = "file:///main.mp4",
            durationMs = 10_000L, trimEndMs = 10_000L, speedMultiplier = 2f
        )
        val overlay = MediaClip(
            id = "overlay", name = "overlay.mp4", uri = "file:///overlay.mp4",
            durationMs = 2_000L, trimEndMs = 2_000L, type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = 7_000L
        )
        val music = AudioTrack("music", "music", "file:///music.m4a", startTimeMs = 8_000L)
        val project = Json.decodeFromString<Project>(legacyJson).copy(
            clips = listOf(main, overlay), audioTracks = listOf(music)
        )

        assertTrue(project.hasLegacyAbsolutePlacementTimingRisk())
        assertEquals(7_000L, project.clips[1].timelineOffsetMs)
        assertEquals(8_000L, project.audioTracks.single().startTimeMs)
    }

    @Test
    fun `current timing version suppresses legacy warning and survives serialization`() {
        val current = Project(
            id = "current", name = "Current", durationMs = 10_000L,
            clips = listOf(
                MediaClip("main", "main", "file:///main", 10_000L,
                    trimEndMs = 10_000L, speedMultiplier = 2f),
                MediaClip("overlay", "overlay", "file:///overlay", 1_000L,
                    trimEndMs = 1_000L, type = ClipType.OVERLAY, trackIndex = 1, timelineOffsetMs = 9_000L)
            ),
            timingModelVersion = Project.CURRENT_TIMING_MODEL_VERSION
        )
        assertFalse(current.hasLegacyAbsolutePlacementTimingRisk())

        val encoded = Json { encodeDefaults = true }.encodeToString(Project.serializer(), current)
        val restored = Json.decodeFromString<Project>(encoded)
        assertEquals(Project.CURRENT_TIMING_MODEL_VERSION, restored.timingModelVersion)
        assertFalse(restored.hasLegacyAbsolutePlacementTimingRisk())
    }

    @Test
    fun `legacy 1x project or project without independent placements does not warn`() {
        val project = Json.decodeFromString<Project>(legacyJson)
        val normal = MediaClip("main", "main", "file:///main", 10_000L, trimEndMs = 10_000L)
        assertFalse(project.copy(clips = listOf(normal), audioTracks = listOf(AudioTrack("a", "a", "uri")))
            .hasLegacyAbsolutePlacementTimingRisk())
        val spedWithoutExtras = project.copy(clips = listOf(normal.copy(speedMultiplier = 2f)))
        assertFalse(spedWithoutExtras.hasLegacyAbsolutePlacementTimingRisk())
    }
}
