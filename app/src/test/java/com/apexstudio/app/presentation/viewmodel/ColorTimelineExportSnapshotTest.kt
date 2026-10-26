package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColorTimelineExportSnapshotTest {
    private val first = MediaClip("one", "One", "file:///one.mp4", 8000,
        trimStartMs = 1000, trimEndMs = 5000)
    private val second = first.copy(id = "two", name = "Two", uri = "file:///two.mp4", speedMultiplier = 2f)
    private val state = EditorState(project = Project("p", "Project", 6000, clips = listOf(first, second)))
    private val settings = ExportSettings(resolution = "1080P", frameRate = 30, aspectRatio = "16:9", bitrateMbps = 12)
    private val grade = ColorStudioGrade(brightness = .1f, saturation = 1.2f)

    @Test fun `comparison and export snapshot retains every main clip trim speed and combined grade`() {
        val plan = buildColorTimelineExportSnapshot(state, AudioStudioState(volume = .6f), settings, grade)
        assertEquals(listOf(first, second), plan.clips)
        assertEquals(1000L, plan.clips[0].trimStartMs)
        assertEquals(2f, plan.clips[1].speedMultiplier, 0f)
        assertEquals(grade, plan.config.colorStudioGrade)
        assertEquals(.6f, plan.config.volume, 0f)
        assertEquals("1080P", plan.config.resolution)
        assertEquals(30, plan.config.fps)
    }

    @Test fun `selection and playhead do not invalidate unchanged composition`() {
        val original = buildColorTimelineExportSnapshot(state, AudioStudioState(), settings, grade)
        val selected = buildColorTimelineExportSnapshot(state.copy(selectedClipId = second.id,
            playerPositionMs = 2000), AudioStudioState(), settings, grade)
        assertEquals(original, selected)
    }

    @Test fun `grade timeline audio and export setting edits invalidate a previously rendered pair`() {
        val original = buildColorTimelineExportSnapshot(state, AudioStudioState(), settings, grade)
        assertNotEquals(original, buildColorTimelineExportSnapshot(state, AudioStudioState(), settings,
            grade.copy(contrast = 1.4f)))
        assertNotEquals(original, buildColorTimelineExportSnapshot(state, AudioStudioState(volume = .2f), settings, grade))
        assertNotEquals(original, buildColorTimelineExportSnapshot(state, AudioStudioState(), settings.copy(frameRate = 24), grade))
        val edited = state.copy(project = state.project!!.copy(clips = listOf(first, second.copy(trimEndMs = 4000))))
        assertNotEquals(original, buildColorTimelineExportSnapshot(edited, AudioStudioState(), settings, grade))
        assertEquals(5000L, original.clips[1].trimEndMs)
    }

    @Test fun `unsupported project layer cannot disappear from either comparison side`() {
        val overlay = first.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1)
        val unsupported = state.copy(project = state.project!!.copy(clips = listOf(first, second, overlay)))
        val failure = runCatching { buildColorTimelineExportSnapshot(unsupported, AudioStudioState(), settings, grade) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(failure?.message.isNullOrBlank())
    }

    @Test fun `unknown global look is blocked instead of silently removed from before and after`() {
        val failure = runCatching { buildColorTimelineExportSnapshot(state.copy(activeFilterId = "missing"),
            AudioStudioState(), settings, grade) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("filter", ignoreCase = true))
    }
}
