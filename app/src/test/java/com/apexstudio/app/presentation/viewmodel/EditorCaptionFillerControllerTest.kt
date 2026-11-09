package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.captions.CaptionFillerDetector
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class EditorCaptionFillerControllerTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun caption(id: String, word: String, start: Long, endExclusive: Long) =
        TextOverlay.of(text = word).copy(
            id = id,
            presetId = "caption_auto",
            startMs = start,
            endMs = endExclusive,
            rangeEndExclusive = true
        )

    @Test
    fun `apply removes approved word windows from main media only and uses one undo step`() {
        val vm = EditorViewModel()
        val video = MediaClip(
            id = "video", name = "video", uri = "file:///video.mp4", durationMs = 1_000L,
            trimEndMs = 1_000L,
            textOverlays = listOf(
                caption("um", "um", 100L, 200L),
                caption("hello", "hello", 300L, 400L),
                caption("uh", "uh", 600L, 700L),
                caption("world", "world", 800L, 900L)
            )
        )
        val overlay = video.copy(
            id = "overlay", type = ClipType.OVERLAY, trackIndex = 2,
            timelineOffsetMs = 900L, durationMs = 50L, trimEndMs = 50L,
            textOverlays = emptyList()
        )
        val music = AudioTrack(
            id = "music", name = "Music", uri = "file:///music.mp3", startTimeMs = 50L,
            trimStartMs = 0L, trimEndMs = 1_000L, sourceDurationMs = 1_000L
        )
        val original = Project(
            id = "project", name = "Project", durationMs = 1_000L,
            clips = listOf(video, overlay), audioTracks = listOf(music)
        )
        vm._state.update { it.copy(project = original, durationMs = 1_000L, selectedClipId = "video") }

        vm.openCaptionFillerReview(CaptionFillerDetector.defaultWords)
        assertEquals(listOf("um", "uh"), vm.captionFillerReview.value.findings.map { it.timedWord.word })
        assertEquals(setOf("um", "uh"), vm.captionFillerReview.value.selectedIds.mapNotNull { id ->
            vm.captionFillerReview.value.findings.firstOrNull { it.id == id }?.timedWord?.word
        }.toSet())

        vm.applyCaptionFillerRemoval()

        val edited = vm.state.value.project!!
        val mainPieces = edited.clips.filter { it.trackIndex == 0 && it.type == ClipType.VIDEO }
        assertEquals(listOf(100L, 400L, 300L), mainPieces.map { it.effectiveDurationMs() })
        assertEquals(800L, mainPieces.sumOf { it.effectiveDurationMs() })
        assertEquals(listOf("hello", "world"), mainPieces.flatMap { it.textOverlays }.map { it.text })
        assertEquals(700L, edited.clips.single { it.id == "overlay" }.timelineOffsetMs)
        assertEquals(listOf(music), edited.audioTracks)
        assertEquals(1, vm.undoStack.size)
        assertFalse(vm.captionFillerReview.value.isOpen)

        val undoJob = vm.undo()
        runBlocking { undoJob.join() }
        assertEquals(original, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
    }

    @Test
    fun `unselected finding is kept and does not contribute to the cut`() {
        val vm = EditorViewModel()
        val video = MediaClip(
            id = "video", name = "video", uri = "file:///video.mp4", durationMs = 1_000L,
            trimEndMs = 1_000L,
            textOverlays = listOf(caption("um", "um", 100L, 200L), caption("uh", "uh", 600L, 700L))
        )
        vm._state.update { it.copy(project = Project("p", "p", 1_000L, clips = listOf(video))) }
        vm.openCaptionFillerReview(CaptionFillerDetector.defaultWords)
        val keep = vm.captionFillerReview.value.findings.single { it.timedWord.word == "uh" }
        vm.setCaptionFillerSelected(keep.id, false)

        vm.applyCaptionFillerRemoval()

        val pieces = vm.state.value.project!!.clips
        assertEquals(900L, pieces.filter { it.trackIndex == 0 }.sumOf { it.effectiveDurationMs() })
        assertTrue(pieces.flatMap { it.textOverlays }.any { it.text == "uh" })
        assertFalse(pieces.flatMap { it.textOverlays }.any { it.text == "um" })
        assertEquals(1, vm.undoStack.size)
    }
}
