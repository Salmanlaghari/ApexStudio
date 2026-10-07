package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.screens.editor.ToolbarSelectionKind
import com.apexstudio.app.ui.screens.editor.resolveToolbarSelection
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the CapCut-style contextual toolbar: selection resolution,
 * mutually-exclusive clip/audio-track selection, per-clip volume,
 * the Replace flow, and the pure beat-grid computation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextualToolbarTest {

    private fun videoClip(id: String = "clip1") = MediaClip(
        id = id,
        name = "video.mp4",
        uri = "content://video",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = ClipType.VIDEO
    )

    private fun audioClip(id: String = "aclip1") = MediaClip(
        id = id,
        name = "song.mp3",
        uri = "content://audio",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = ClipType.AUDIO
    )

    private fun audioTrack(id: String = "track1") = AudioTrack(
        id = id,
        name = "Music",
        uri = "content://track",
        trimEndMs = 10_000L
    )

    private fun stateWith(
        clips: List<MediaClip> = emptyList(),
        tracks: List<AudioTrack> = emptyList(),
        selectedClipId: String? = null,
        selectedAudioTrackId: String? = null
    ) = EditorState(
        project = Project(id = "p", name = "p", durationMs = 10_000L, clips = clips, audioTracks = tracks),
        selectedClipId = selectedClipId,
        selectedAudioTrackId = selectedAudioTrackId
    )

    // --- resolveToolbarSelection ---

    @Test
    fun `no project resolves to NONE`() {
        assertEquals(ToolbarSelectionKind.NONE, resolveToolbarSelection(EditorState()))
    }

    @Test
    fun `no selection resolves to NONE`() {
        assertEquals(
            ToolbarSelectionKind.NONE,
            resolveToolbarSelection(stateWith(clips = listOf(videoClip())))
        )
    }

    @Test
    fun `video clip selection resolves to VIDEO`() {
        assertEquals(
            ToolbarSelectionKind.VIDEO,
            resolveToolbarSelection(stateWith(clips = listOf(videoClip()), selectedClipId = "clip1"))
        )
    }

    @Test
    fun `overlay clip selection resolves to VIDEO`() {
        val overlay = videoClip().copy(type = ClipType.OVERLAY)
        assertEquals(
            ToolbarSelectionKind.VIDEO,
            resolveToolbarSelection(stateWith(clips = listOf(overlay), selectedClipId = "clip1"))
        )
    }

    @Test
    fun `audio clip selection resolves to AUDIO`() {
        assertEquals(
            ToolbarSelectionKind.AUDIO,
            resolveToolbarSelection(stateWith(clips = listOf(audioClip()), selectedClipId = "aclip1"))
        )
    }

    @Test
    fun `image clip selection resolves to PHOTO`() {
        val photo = videoClip(id = "photo1").copy(
            name = "sunset.jpg",
            uri = "content://photo",
            durationMs = 3000L,
            trimEndMs = 3000L,
            type = ClipType.IMAGE
        )
        assertEquals(
            ToolbarSelectionKind.PHOTO,
            resolveToolbarSelection(stateWith(clips = listOf(photo), selectedClipId = "photo1"))
        )
    }

    @Test
    fun `audio track selection resolves to AUDIO`() {
        assertEquals(
            ToolbarSelectionKind.AUDIO,
            resolveToolbarSelection(
                stateWith(
                    clips = listOf(videoClip()),
                    tracks = listOf(audioTrack()),
                    selectedAudioTrackId = "track1"
                )
            )
        )
    }

    @Test
    fun `unknown selected ids resolve to NONE`() {
        assertEquals(
            ToolbarSelectionKind.NONE,
            resolveToolbarSelection(
                stateWith(
                    clips = listOf(videoClip()),
                    selectedClipId = "missing",
                    selectedAudioTrackId = "missing"
                )
            )
        )
    }

    // --- Selection mutual exclusivity ---

    @Test
    fun `selectAudioTrack clears clip selection`() {
        val vm = EditorViewModel()
        vm.selectClip("clip1")
        vm.selectAudioTrack("track1")
        assertEquals("track1", vm.state.value.selectedAudioTrackId)
        assertNull(vm.state.value.selectedClipId)
    }

    @Test
    fun `selectClip clears audio track selection`() {
        val vm = EditorViewModel()
        vm.selectAudioTrack("track1")
        vm.selectClip("clip1")
        assertEquals("clip1", vm.state.value.selectedClipId)
        assertNull(vm.state.value.selectedAudioTrackId)
    }

    @Test
    fun `clearSelection clears both`() {
        val vm = EditorViewModel()
        vm.selectClip("clip1")
        vm.selectAudioTrack("track1")
        vm.clearSelection()
        assertNull(vm.state.value.selectedClipId)
        assertNull(vm.state.value.selectedAudioTrackId)
    }

    // --- Per-clip volume ---

    @Test
    fun `setClipVolume coerces into 0 to 2 range`() {
        val vm = EditorViewModel()
        vm._state.update {
            it.copy(project = Project(id = "p", name = "p", durationMs = 10_000L, clips = listOf(videoClip())))
        }
        vm.setClipVolume("clip1", 5f)
        assertEquals(2f, vm.state.value.project!!.clips.first().volume, 0.001f)
        vm.setClipVolume("clip1", -1f)
        assertEquals(0f, vm.state.value.project!!.clips.first().volume, 0.001f)
        vm.setClipVolume("clip1", 1.5f)
        assertEquals(1.5f, vm.state.value.project!!.clips.first().volume, 0.001f)
    }

    @Test
    fun `setClipVolume on missing clip is a no-op`() {
        val vm = EditorViewModel()
        vm.setClipVolume("nope", 1.5f) // must not crash
    }

    // --- Replace flow ---

    @Test
    fun `replaceClipMedia keeps identity and timeline position but swaps media`() {
        val vm = EditorViewModel()
        val original = videoClip().copy(timelineOffsetMs = 2000L, trimStartMs = 1000L, trimEndMs = 8000L)
        vm._state.update {
            it.copy(project = Project(id = "p", name = "p", durationMs = 10_000L, clips = listOf(original)))
        }
        vm.setPendingReplaceClip("clip1")
        val meta = MediaMetadata(
            uri = "content://newvideo",
            name = "new.mp4",
            durationMs = 20_000L,
            width = 1920,
            height = 1080,
            fps = 30,
            type = ClipType.VIDEO
        )
        vm.replaceClipMedia("clip1", meta)
        val replaced = vm.state.value.project!!.clips.first()
        assertEquals("clip1", replaced.id)
        assertEquals("content://newvideo", replaced.uri)
        assertEquals("new.mp4", replaced.name)
        assertEquals(20_000L, replaced.durationMs)
        assertEquals(2000L, replaced.timelineOffsetMs)
        assertEquals(1000L, replaced.trimStartMs)
        assertEquals(8000L, replaced.trimEndMs)
        assertNull(vm.state.value.pendingReplaceClipId)
    }

    @Test
    fun `replaceClipMedia clamps trims to shorter replacement`() {
        val vm = EditorViewModel()
        val original = videoClip().copy(trimStartMs = 1000L, trimEndMs = 9000L)
        vm._state.update {
            it.copy(project = Project(id = "p", name = "p", durationMs = 10_000L, clips = listOf(original)))
        }
        val meta = MediaMetadata(
            uri = "content://short",
            name = "short.mp4",
            durationMs = 3000L,
            width = 1920,
            height = 1080,
            fps = 30,
            type = ClipType.VIDEO
        )
        vm.replaceClipMedia("clip1", meta)
        val replaced = vm.state.value.project!!.clips.first()
        assertEquals(3000L, replaced.durationMs)
        assertTrue(replaced.trimEndMs <= 3000L)
        assertTrue(replaced.trimStartMs < replaced.trimEndMs)
    }

    @Test
    fun `replaceAudioTrackMedia swaps uri and resets trims`() {
        val vm = EditorViewModel()
        val track = audioTrack().copy(volume = 0.5f, trimStartMs = 1000L, trimEndMs = 5000L)
        vm._state.update {
            it.copy(project = Project(id = "p", name = "p", durationMs = 10_000L, audioTracks = listOf(track)))
        }
        val meta = MediaMetadata(
            uri = "content://newsong",
            name = "new.mp3",
            durationMs = 12_000L,
            width = 0,
            height = 0,
            fps = 0,
            type = ClipType.AUDIO
        )
        vm.replaceAudioTrackMedia("track1", meta)
        val replaced = vm.state.value.project!!.audioTracks.first()
        assertEquals("track1", replaced.id)
        assertEquals("content://newsong", replaced.uri)
        assertEquals(0L, replaced.trimStartMs)
        assertEquals(12_000L, replaced.trimEndMs)
        assertEquals(0.5f, replaced.volume, 0.001f) // volume preserved
        assertTrue(vm.state.value.beatMarkersMs.isEmpty()) // stale beats dropped
    }

    // --- Beat grid (pure function) ---

    @Test
    fun `computeBeatGrid finds steady beats in synthetic envelope`() {
        // 120 BPM over 8 seconds: onset spikes every 500ms.
        val durationMs = 8000L
        val n = 800
        val envelope = FloatArray(n) { 0.1f }
        var t = 250L
        while (t < durationMs) {
            val idx = (t * n / durationMs).toInt().coerceIn(0, n - 1)
            envelope[idx] = 1.0f
            t += 500L
        }
        val beats = computeBeatGrid(envelope, durationMs)
        assertTrue("expected beats, got $beats", beats.size >= 10)
        // Beats should be roughly 500ms apart.
        val intervals = beats.zipWithNext { a, b -> b - a }
        val avgInterval = intervals.average()
        assertTrue("avg interval $avgInterval should be near 500ms", avgInterval in 350.0..650.0)
    }

    @Test
    fun `computeBeatGrid returns empty for flat envelope`() {
        val beats = computeBeatGrid(FloatArray(800) { 0.1f }, 8000L)
        assertTrue(beats.isEmpty())
    }

    @Test
    fun `computeBeatGrid handles degenerate input`() {
        assertTrue(computeBeatGrid(FloatArray(0), 8000L).isEmpty())
        assertTrue(computeBeatGrid(FloatArray(800) { 0.5f }, 0L).isEmpty())
    }
}
