package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.timeline.AudioTrack as SharedTimelineAudioTrack
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.StylisticEffectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Locks default-on main-track ripple behavior and its explicit opt-out contract. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineRipplePreservationTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun video(id: String) = MediaClip(
        id = id, name = id, uri = "file:///$id.mp4", durationMs = 10_000L,
        trimEndMs = 10_000L, type = ClipType.VIDEO
    )

    private fun overlay(id: String, offset: Long) = video(id).copy(
        type = ClipType.OVERLAY, trackIndex = 2, timelineOffsetMs = offset
    )

    private fun audioTrack(id: String, startTimeMs: Long, sourceSpanMs: Long = 10_000L) = AudioTrack(
        id = id, name = id, uri = "file:///$id.m4a", startTimeMs = startTimeMs,
        trimEndMs = sourceSpanMs, sourceDurationMs = sourceSpanMs
    )

    private fun vm(vararg clips: MediaClip, audioTracks: List<AudioTrack> = emptyList()) = EditorViewModel().also { vm ->
        vm._state.update {
            it.copy(project = Project(id = "p", name = "p", durationMs = 40_000L,
                clips = clips.toList(), audioTracks = audioTracks), durationMs = 40_000L)
        }
        vm._audio.update { it.copy(tracks = audioTracks) }
    }

    private fun EditorViewModel.offset(id: String) =
        state.value.project!!.clips.first { it.id == id }.timelineOffsetMs

    private fun isolatedDataStoreContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "timeline-ripple-${System.nanoTime()}")
        val files = File(root, "files").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
        }
        return context
    }

    @Test
    fun `V1 trim shifts overlays at and after boundary but not before it`() {
        val vm = vm(video("v1"), overlay("before", 4_000L),
            overlay("at", 5_000L), overlay("after", 12_000L))
        vm.trimClip("v1", 0L, 5_000L)
        assertEquals(4_000L, vm.offset("before"))
        assertEquals(0L, vm.offset("at"))
        assertEquals(7_000L, vm.offset("after"))
    }

    @Test
    fun `V1 trim ripples independent audio by speed-aware output delta in both state mirrors`() {
        val editor = vm(
            video("fast").copy(speedMultiplier = 2f),
            audioTracks = listOf(
                audioTrack("before", 2_999L),
                audioTrack("at", 3_000L),
                audioTrack("after", 7_000L)
            )
        )

        editor.trimClip("fast", 0L, 6_000L)

        val projectTracks = editor.state.value.project!!.audioTracks
        assertEquals(listOf(2_999L, 1_000L, 5_000L), projectTracks.map { it.startTimeMs })
        assertEquals(projectTracks, editor.audio.value.tracks)
    }

    @Test
    fun `V1 outward trim shifts audio at the old end to the expanded boundary`() {
        val editor = vm(
            video("expand").copy(trimStartMs = 1_000L, trimEndMs = 3_000L),
            audioTracks = listOf(audioTrack("before", 1_999L), audioTrack("at", 2_000L))
        )

        editor.trimClip("expand", 500L, 3_500L)

        assertEquals(listOf(1_999L, 3_000L), editor.state.value.project!!.audioTracks.map { it.startTimeMs })
        assertEquals(listOf(1_999L, 3_000L), editor.audio.value.tracks.map { it.startTimeMs })
    }

    @Test
    fun `audio ripple uses an inclusive boundary and clamps and saturates project time`() {
        val before = audioTrack("before", 99L)
        val at = audioTrack("at", 100L).copy(fadeInMs = 500L)
        val shifted = rippleAudioTracks(listOf(before, at), anchorMs = 100L, deltaMs = -200L)

        assertEquals(listOf(99L, 0L), shifted.map { it.startTimeMs })
        assertEquals(500L, shifted.last().fadeInMs)
        assertEquals(Long.MAX_VALUE, rippleAudioTracks(
            listOf(audioTrack("max", Long.MAX_VALUE)), anchorMs = 0L, deltaMs = 1L
        ).single().startTimeMs)
    }

    @Test
    fun `V1 head and tail extensions commit larger clip and project durations`() {
        val head = vm(video("head").copy(trimStartMs = 1_000L, trimEndMs = 3_000L))
        head.trimClip("head", 500L, 3_000L)
        val headProject = head.state.value.project!!
        val headClip = headProject.clips.single()
        assertEquals(500L, headClip.trimStartMs)
        assertEquals(3_000L, headClip.trimEndMs)
        assertEquals(2_500L, headClip.effectiveDurationMs())
        assertEquals(2_500L, headProject.durationMs)
        assertEquals(2_500L, head.state.value.durationMs)
        assertTrue(head.state.value.canUndo)

        val tail = vm(video("tail").copy(trimStartMs = 1_000L, trimEndMs = 3_000L))
        tail.trimClip("tail", 1_000L, 3_500L)
        val tailProject = tail.state.value.project!!
        val tailClip = tailProject.clips.single()
        assertEquals(1_000L, tailClip.trimStartMs)
        assertEquals(3_500L, tailClip.trimEndMs)
        assertEquals(2_500L, tailClip.effectiveDurationMs())
        assertEquals(2_500L, tailProject.durationMs)
        assertEquals(2_500L, tail.state.value.durationMs)
        assertTrue(tail.state.value.canUndo)
    }

    // DataStore writes use real dispatchers; this test uses an unconfined Main and
    // awaits the durable-save marker before reading the persisted project.
    @Test
    fun `V1 outward trim autosaves both expanded source bounds and duration`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = isolatedDataStoreContext()
        val projectId = "v1-trim-autosave-${System.nanoTime()}"
        val repository = com.apexstudio.app.data.repository.ProjectRepository(context)
        val initialClip = video("main").copy(trimStartMs = 1_000L, trimEndMs = 3_000L)
        var editor: EditorViewModel? = null
        try {
            repository.saveProject(Project(
                projectId, "V1 trim autosave", 2_000L,
                clips = listOf(initialClip), audioTracks = listOf(audioTrack("music", 2_000L, 1_000L))
            ))
            val loadedEditor = EditorViewModel(context = context, projectId = projectId)
            editor = loadedEditor
            withTimeout(10_000L) {
                loadedEditor.state.first { it.project?.id == projectId }
            }

            // One committed trim combines both outward edges; the UI tests above
            // exercise each grip independently before this repository assertion.
            val priorAutoSaveMs = loadedEditor.state.value.lastAutoSaveMs
            loadedEditor.trimClip("main", 500L, 3_500L)
            shadowOf(Looper.getMainLooper()).idle()
            withTimeout(30_000L) {
                loadedEditor.state.first { it.lastAutoSaveMs > priorAutoSaveMs }
            }
            val savedProject = withContext(Dispatchers.IO) {
                repository.loadAll().first().single { it.id == projectId }
            }
            val savedClip = savedProject.clips.single()
            assertEquals(4_000L, savedProject.durationMs)
            assertEquals(500L, savedClip.trimStartMs)
            assertEquals(3_500L, savedClip.trimEndMs)
            assertEquals(3_000L, savedClip.effectiveDurationMs())
            assertEquals(3_000L, savedProject.audioTracks.single().startTimeMs)
            assertEquals(3_000L, loadedEditor.audio.value.tracks.single().startTimeMs)
            val typedAudio = savedProject.timeline.tracks.filterIsInstance<SharedTimelineAudioTrack>().single()
            assertEquals(3_000L, typedAudio.clips.single().inMs)
            assertEquals(4_000L, typedAudio.clips.single().outMs)
        } finally {
            editor?.let { viewModel ->
                ViewModelStore().apply {
                    put("timeline-ripple-autosave", viewModel)
                    clear()
                }
            }
        }
    }

    @Test
    fun `constant-speed V1 trim ripples by output duration at 2x and half speed`() {
        val fast = vm(video("fast").copy(speedMultiplier = 2f), overlay("fast-after", 6_000L))
        fast.trimClip("fast", 0L, 6_000L)
        assertEquals(3_000L, fast.state.value.project!!.clips.first { it.id == "fast" }.effectiveDurationMs())
        assertEquals(4_000L, fast.offset("fast-after"))

        val slow = vm(video("slow").copy(speedMultiplier = 0.5f), overlay("slow-after", 22_000L))
        slow.trimClip("slow", 0L, 6_000L)
        assertEquals(12_000L, slow.state.value.project!!.clips.first { it.id == "slow" }.effectiveDurationMs())
        assertEquals(14_000L, slow.offset("slow-after"))
    }

    @Test
    fun `V1 delete ripples from cumulative clip start and clamps offsets to zero`() {
        val vm = vm(video("first"), video("second"), overlay("before", 5_000L),
            overlay("at", 10_000L), overlay("after", 25_000L), audioTracks = listOf(
                audioTrack("before-audio", 9_999L), audioTrack("at-audio", 10_000L), audioTrack("after-audio", 15_000L)
            ))
        vm.deleteClip("second")
        assertEquals(5_000L, vm.offset("before"))
        assertEquals(0L, vm.offset("at"))
        assertEquals(15_000L, vm.offset("after"))
        assertEquals(listOf(9_999L, 0L, 5_000L), vm.state.value.project!!.audioTracks.map { it.startTimeMs })
        assertEquals(vm.state.value.project!!.audioTracks, vm.audio.value.tracks)
    }

    @Test
    fun `V1 duplicate preserves existing append-boundary ripple behavior`() {
        val vm = vm(video("first"), video("second"), overlay("before", 19_000L),
            overlay("at", 20_000L), overlay("after", 25_000L), audioTracks = listOf(
                audioTrack("before-audio", 19_999L), audioTrack("at-audio", 20_000L), audioTrack("after-audio", 25_000L)
            ))
        vm.duplicateClip("first")
        assertEquals(19_000L, vm.offset("before"))
        assertEquals(30_000L, vm.offset("at"))
        assertEquals(35_000L, vm.offset("after"))
        assertEquals("first", vm.state.value.project!!.clips.last().name)
        assertEquals(listOf(19_999L, 30_000L, 35_000L), vm.state.value.project!!.audioTracks.map { it.startTimeMs })
        assertEquals(vm.state.value.project!!.audioTracks, vm.audio.value.tracks)
    }

    @Test
    fun `V1 split does not ripple overlays`() {
        val vm = vm(video("v1"), overlay("overlay", 12_000L), audioTracks = listOf(audioTrack("music", 12_000L)))
        vm.splitClip("v1", 5_000L)
        assertEquals(12_000L, vm.offset("overlay"))
        assertEquals(12_000L, vm.audio.value.tracks.single().startTimeMs)
    }

    @Test
    fun `constant-speed V1 split converts project time to source at 2x and half speed`() {
        val fast = vm(video("fast").copy(speedMultiplier = 2f))
        fast.splitClip("fast", 2_500L)
        val fastHalves = fast.state.value.project!!.clips
        assertEquals(5_000L, fastHalves.first { it.id == "fast" }.trimEndMs)
        assertEquals(5_000L, fastHalves.last().trimStartMs)
        assertEquals(2_500L, fastHalves[0].effectiveDurationMs())
        assertEquals(2_500L, fastHalves[1].effectiveDurationMs())
        assertEquals(10_000L, fastHalves[1].durationMs)

        val slow = vm(video("slow").copy(speedMultiplier = 0.5f))
        slow.splitClip("slow", 5_000L)
        val slowHalves = slow.state.value.project!!.clips
        assertEquals(2_500L, slowHalves.first { it.id == "slow" }.trimEndMs)
        assertEquals(2_500L, slowHalves.last().trimStartMs)
        assertEquals(5_000L, slowHalves[0].effectiveDurationMs())
        assertEquals(15_000L, slowHalves[1].effectiveDurationMs())
        assertEquals(10_000L, slowHalves[1].durationMs)
    }

    @Test
    fun `overlay trim and delete never ripple another overlay`() {
        val vm = vm(video("v1"), overlay("edit", 3_000L), overlay("other", 12_000L),
            audioTracks = listOf(audioTrack("music", 12_000L)))
        vm.trimClip("edit", 1_000L, 4_000L)
        assertEquals(12_000L, vm.offset("other"))
        assertEquals(12_000L, vm.state.value.project!!.audioTracks.single().startTimeMs)
        vm.deleteClip("edit")
        assertEquals(12_000L, vm.offset("other"))
        assertEquals(12_000L, vm.state.value.project!!.audioTracks.single().startTimeMs)
    }


    @Test
    fun `bulk delete ripples overlays and one undo restores selection targets`() {
        val vm = vm(video("first"), video("middle"), video("last"), overlay("at", 30_000L), audioTracks = listOf(
            audioTrack("before-last", 19_999L), audioTrack("at-last", 20_000L), audioTrack("after-last", 30_000L)
        ))
        vm._state.update { it.copy(selectedClipIds = setOf("first", "last"), multiSelectMode = true) }
        vm.deleteSelectedClips()
        assertEquals(10_000L, vm.offset("at"))
        assertEquals(listOf(9_999L, 0L, 10_000L), vm.audio.value.tracks.map { it.startTimeMs })
        assertEquals(listOf("middle", "at"), vm.state.value.project!!.clips.map { it.id })
        vm.undo()
        assertEquals(listOf("first", "middle", "last", "at"), vm.state.value.project!!.clips.map { it.id })
        assertEquals(listOf(19_999L, 20_000L, 30_000L), vm.audio.value.tracks.map { it.startTimeMs })
        vm.redo()
        assertEquals(listOf(9_999L, 0L, 10_000L), vm.audio.value.tracks.map { it.startTimeMs })
    }

    @Test
    fun `bulk duplicate appends V1 copies, ripples once, and regenerates child ids`() {
        val source = video("source").copy(
            speedMultiplier = 2f,
            textOverlays = listOf(TextOverlay(id = "title")),
            stickers = listOf(StickerOverlay(id = "sticker"))
        )
        val vm = vm(source, video("tail"), overlay("downstream", 20_000L),
            audioTracks = listOf(audioTrack("before-audio", 14_999L), audioTrack("at-audio", 15_000L), audioTrack("after-audio", 20_000L)))
        vm._state.update { it.copy(selectedClipIds = setOf("source"), multiSelectMode = true) }
        vm.duplicateSelectedClips()
        val clips = vm.state.value.project!!.clips
        val copy = clips.last { it.id != "source" && it.id != "tail" && it.id != "downstream" }
        assertEquals(25_000L, clips.first { it.id == "downstream" }.timelineOffsetMs)
        assertEquals(listOf(14_999L, 20_000L, 25_000L), vm.audio.value.tracks.map { it.startTimeMs })
        assertEquals(source.effectiveDurationMs(), copy.effectiveDurationMs())
        assertEquals(setOf("title"), source.textOverlays.map { it.id }.toSet())
        assertEquals(setOf("sticker"), source.stickers.map { it.id }.toSet())
        assertTrue(copy.textOverlays.map { it.id }.none { it == "title" })
        assertTrue(copy.stickers.map { it.id }.none { it == "sticker" })
        vm.undo()
        assertEquals(3, vm.state.value.project!!.clips.size)
        assertEquals(listOf(14_999L, 15_000L, 20_000L), vm.audio.value.tracks.map { it.startTimeMs })
    }

    @Test
    fun `bulk duplicate places selected overlay after its rippled source`() {
        val vm = vm(video("source").copy(speedMultiplier = 2f), video("tail"), overlay("selected-overlay", 20_000L))
        vm._state.update { it.copy(selectedClipIds = setOf("source", "selected-overlay"), multiSelectMode = true) }

        vm.duplicateSelectedClips()

        val clips = vm.state.value.project!!.clips
        val rippledSource = clips.single { it.id == "selected-overlay" }
        val copy = clips.last { it.type == ClipType.OVERLAY && it.id != "selected-overlay" }
        assertEquals(25_000L, rippledSource.timelineOffsetMs)
        assertEquals(rippledSource.timelineOffsetMs + rippledSource.effectiveDurationMs(), copy.timelineOffsetMs)
    }

    @Test
    fun `bulk speed filter and LUT persist as one selection action each`() {
        val vm = vm(video("one"), video("two"))
        vm._state.update { it.copy(selectedClipIds = setOf("one", "two"), multiSelectMode = true) }
        vm.applySpeedToSelectedClips(1.5f)
        assertTrue(vm.state.value.project!!.clips.all { it.speedMultiplier == 1.5f && it.speedPoints.isEmpty() })
        val filter = GpuFilterConfig(stylisticEffect = StylisticEffectType.SEPIA)
        vm.applyFilterToSelectedClips(filter)
        assertTrue(vm.state.value.project!!.clips.all { it.gpuFilterConfig == filter })
        vm.applyLutToSelectedClips(GpuColorProfiles.CINEMATIC.id, 0.7f)
        assertTrue(vm.state.value.project!!.clips.all { it.gpuFilterConfig.activeProfileId == GpuColorProfiles.CINEMATIC.id })
    }
    @Test
    fun `ripple editing defaults on and can be toggled off and on`() {
        val editor = vm(video("toggle"))

        assertEquals(true, editor.state.value.rippleEditing)
        editor.toggleRippleEditing()
        assertEquals(false, editor.state.value.rippleEditing)
        editor.toggleRippleEditing()
        assertEquals(true, editor.state.value.rippleEditing)
    }

    @Test
    fun `ripple disabled preserves downstream layers for single V1 trim duplicate and delete`() {
        val trimmed = vm(
            video("trim"),
            overlay("trim-later", 12_000L),
            audioTracks = listOf(audioTrack("trim-audio", 12_000L))
        )
        trimmed._state.update { it.copy(rippleEditing = false) }
        trimmed.trimClip("trim", 0L, 5_000L)
        assertEquals(12_000L, trimmed.offset("trim-later"))
        assertEquals(12_000L, trimmed.state.value.project!!.audioTracks.single().startTimeMs)

        val duplicated = vm(
            video("duplicate"),
            overlay("duplicate-later", 12_000L),
            audioTracks = listOf(audioTrack("duplicate-audio", 12_000L))
        )
        duplicated._state.update { it.copy(rippleEditing = false) }
        duplicated.duplicateClip("duplicate")
        assertEquals(12_000L, duplicated.offset("duplicate-later"))
        assertEquals(12_000L, duplicated.state.value.project!!.audioTracks.single().startTimeMs)

        val deleted = vm(
            video("delete"),
            overlay("delete-later", 12_000L),
            audioTracks = listOf(audioTrack("delete-audio", 12_000L))
        )
        deleted._state.update { it.copy(rippleEditing = false) }
        deleted.deleteClip("delete")
        assertEquals(12_000L, deleted.offset("delete-later"))
        assertEquals(12_000L, deleted.state.value.project!!.audioTracks.single().startTimeMs)
    }

    @Test
    fun `ripple disabled preserves downstream layers for multi-select delete and duplicate`() {
        val deleted = vm(
            video("first-delete"),
            video("second-delete"),
            overlay("delete-later", 25_000L),
            audioTracks = listOf(audioTrack("delete-audio", 25_000L))
        )
        deleted._state.update {
            it.copy(rippleEditing = false, selectedClipIds = setOf("first-delete"), multiSelectMode = true)
        }
        assertTrue(deleted.deleteSelectedClips())
        assertEquals(25_000L, deleted.offset("delete-later"))
        assertEquals(25_000L, deleted.state.value.project!!.audioTracks.single().startTimeMs)

        val duplicated = vm(
            video("first-duplicate"),
            video("second-duplicate"),
            overlay("duplicate-later", 20_000L),
            audioTracks = listOf(audioTrack("duplicate-audio", 20_000L))
        )
        duplicated._state.update {
            it.copy(rippleEditing = false, selectedClipIds = setOf("first-duplicate"), multiSelectMode = true)
        }
        assertTrue(duplicated.duplicateSelectedClips())
        assertEquals(20_000L, duplicated.offset("duplicate-later"))
        assertEquals(20_000L, duplicated.state.value.project!!.audioTracks.single().startTimeMs)
    }

    @Test
    fun `grouped selection keeps clips together during reorder`() {
        val vm = vm(video("a"), video("b"), video("tail"))
        vm._state.update { it.copy(selectedClipIds = setOf("a", "b"), multiSelectMode = true) }
        assertTrue(vm.groupSelectedClips())
        val groupIds = vm.state.value.project!!.clips.filter { it.id == "a" || it.id == "b" }.map { it.groupId }.toSet()
        assertEquals(1, groupIds.size)
        vm.reorderClips(0, 2)
        assertEquals(listOf("tail", "a", "b"), vm.state.value.project!!.clips.map { it.id })
    }

}
