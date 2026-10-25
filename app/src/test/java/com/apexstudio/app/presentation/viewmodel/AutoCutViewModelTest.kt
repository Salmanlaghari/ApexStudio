package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoCutViewModelTest {
    @Test
    fun `autocut appends normal trimmed clips and one undo restores the original project`() {
        val existing = video("existing", 1_000L)
        val firstSource = video("source-a", 5_000L)
        val secondSource = video("source-b", 5_000L)
        val music = AudioTrack(
            id = "music",
            name = "Analyzed music",
            uri = "file:///music.wav",
            trimStartMs = 0L,
            trimEndMs = 20_000L,
            sourceDurationMs = 20_000L
        )
        val original = Project(
            id = "project",
            name = "Keep this edit",
            durationMs = 20_000L,
            clips = listOf(existing, firstSource, secondSource),
            audioTracks = listOf(music)
        )
        val vm = EditorViewModel()
        vm._state.update {
            it.copy(
                project = original,
                durationMs = original.durationMs,
                selectedClipIds = setOf(firstSource.id, secondSource.id),
                multiSelectMode = true,
                selectedAudioTrackId = music.id,
                beatSourceTrackId = music.id,
                beatMarkersByTrackUri = mapOf(music.uri to listOf(0L, 11_500L, 12_000L, 12_500L, 13_000L))
            )
        }

        assertTrue(vm.autoCutSelectedClips())

        val after = vm.state.value.project!!
        assertEquals(listOf(existing, firstSource, secondSource), after.clips.take(3))
        val generated = after.clips.takeLast(2)
        assertEquals(2, generated.size)
        assertEquals(listOf(1_000L, 1_000L), generated.map { it.effectiveDurationMs() })
        assertEquals(listOf(1_000L, 1_000L), generated.map { it.trimEndMs - it.trimStartMs })
        assertEquals(listOf(0, 0), generated.map { it.trackIndex })
        assertEquals(listOf("source-a", "source-b"), generated.map { it.name })
        assertNotEquals(firstSource.id, generated.first().id)
        assertNotEquals(secondSource.id, generated.last().id)
        assertTrue(vm.state.value.canUndo)

        vm.undo()

        assertEquals(original, vm.state.value.project)
        assertEquals(original.durationMs, vm.state.value.durationMs)
        assertTrue(vm.state.value.canRedo)
    }

    @Test
    fun `does not apply beats from a selected non-music track`() {
        val first = video("first", 5_000L)
        val second = video("second", 5_000L)
        val voice = AudioTrack(
            id = "voice",
            name = "Voiceover",
            uri = "file:///voice.wav",
            trimEndMs = 20_000L,
            sourceDurationMs = 20_000L,
            kind = AudioTrack.Kind.SFX
        )
        val original = Project(
            id = "project",
            name = "Keep this edit",
            durationMs = 20_000L,
            clips = listOf(first, second),
            audioTracks = listOf(voice)
        )
        val vm = EditorViewModel()
        vm._state.update {
            it.copy(
                project = original,
                selectedClipIds = setOf(first.id, second.id),
                selectedAudioTrackId = voice.id,
                beatSourceTrackId = voice.id,
                beatMarkersByTrackUri = mapOf(voice.uri to listOf(0L, 1_000L, 2_000L, 3_000L))
            )
        }

        assertFalse(vm.autoCutSelectedClips())
        assertEquals(original, vm.state.value.project)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `requires an explicit selected music lane instead of a stale beat-source id`() {
        val first = video("first", 5_000L)
        val second = video("second", 5_000L)
        val music = AudioTrack(
            id = "music",
            name = "Music",
            uri = "file:///music.wav",
            trimEndMs = 20_000L,
            sourceDurationMs = 20_000L
        )
        val original = Project(
            id = "project",
            name = "Keep this edit",
            durationMs = 20_000L,
            clips = listOf(first, second),
            audioTracks = listOf(music)
        )
        val vm = EditorViewModel()
        vm._state.update {
            it.copy(
                project = original,
                selectedClipIds = setOf(first.id, second.id),
                selectedAudioTrackId = null,
                beatSourceTrackId = music.id,
                beatMarkersByTrackUri = mapOf(music.uri to listOf(0L, 1_000L, 2_000L, 3_000L))
            )
        }

        assertFalse(vm.autoCutSelectedClips())
        assertEquals(original, vm.state.value.project)
        assertFalse(vm.state.value.canUndo)
    }

    private fun video(id: String, durationMs: Long) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = durationMs,
        trimEndMs = durationMs
    )
}
