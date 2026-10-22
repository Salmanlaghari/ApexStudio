package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.VideoFitMode
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoFitPersistenceTest {
    @Test
    fun `legacy saved project without clip fit mode defaults to fit`() {
        val legacyJson = """{"id":"legacy","name":"Legacy","durationMs":1000,"clips":[{"id":"v1","name":"old.mp4","uri":"file:///old.mp4","durationMs":1000,"trimEndMs":1000}]}"""

        val restored = Json.decodeFromString<Project>(legacyJson)

        assertEquals(VideoFitMode.FIT, restored.clips.single().videoFitMode)
    }

    @Test
    fun `cover mode survives project serialization and reload`() {
        val project = Project(
            id = "cover-project",
            name = "Cover project",
            durationMs = 1_000L,
            clips = listOf(MediaClip(
                id = "v1", name = "portrait.mp4", uri = "file:///portrait.mp4",
                durationMs = 1_000L, trimEndMs = 1_000L, videoFitMode = VideoFitMode.COVER
            ))
        )
        val json = Json { encodeDefaults = true }.encodeToString(Project.serializer(), project)

        assertEquals(VideoFitMode.COVER, Json.decodeFromString<Project>(json).clips.single().videoFitMode)
    }

    @Test
    fun `fit mode undo restores the whole project and leaves other clips unchanged`() {
        val first = MediaClip(
            id = "first", name = "first.mp4", uri = "file:///first.mp4",
            durationMs = 1_000L, trimEndMs = 1_000L
        )
        val other = MediaClip(
            id = "other", name = "other.mp4", uri = "file:///other.mp4",
            durationMs = 2_000L, trimEndMs = 2_000L, videoFitMode = VideoFitMode.COVER
        )
        val original = Project("project", "Keep project metadata", 3_000L, clips = listOf(first, other))
        val vm = EditorViewModel()
        vm._state.update { it.copy(project = original, durationMs = original.durationMs) }

        vm.setVideoFitMode(first.id, VideoFitMode.COVER)

        assertEquals(VideoFitMode.COVER, vm.state.value.project!!.clips[0].videoFitMode)
        assertEquals(other, vm.state.value.project!!.clips[1])
        assertEquals(original.name, vm.state.value.project!!.name)
        assertTrue(vm.state.value.canUndo)

        vm.undo()

        assertEquals(original, vm.state.value.project)
        assertFalse(vm.state.value.canUndo)
        assertTrue(vm.state.value.canRedo)
    }
}
