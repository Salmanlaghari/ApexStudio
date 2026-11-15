package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class EditorProjectAspectRatioControllerTest {
    private fun mainClip() = MediaClip(
        id = "main",
        name = "Main video",
        uri = "file:///main.mp4",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = ClipType.VIDEO
    )

    @Test
    fun lateDetectedVerticalSourceSetsProjectAndExportDefaultsTogether() {
        val vm = EditorViewModel().apply {
            _state.update {
                it.copy(project = Project("late-aspect", "Late aspect", 10_000L, clips = listOf(mainClip())))
            }
        }

        vm.applyDetectedProjectVideoAspectRatio(9f / 16f)

        assertEquals(9f / 16f, vm.state.value.project!!.videoAspectRatio!!, 0.0001f)
        assertEquals("9:16", vm.export.value.settings.aspectRatio)
    }

    @Test
    fun detectionDoesNotOverrideExistingProjectAspectOrManualExportChoice() {
        val vm = EditorViewModel().apply {
            _state.update {
                it.copy(project = Project(
                    "chosen-aspect", "Chosen aspect", 10_000L,
                    clips = listOf(mainClip()), videoAspectRatio = 16f / 9f
                ))
            }
            _export.update { it.copy(settings = it.settings.copy(aspectRatio = "1:1")) }
        }

        vm.applyDetectedProjectVideoAspectRatio(9f / 16f)

        assertEquals(16f / 9f, vm.state.value.project!!.videoAspectRatio!!, 0.0001f)
        assertEquals("1:1", vm.export.value.settings.aspectRatio)
    }

    @Test
    fun importingExifRotatedStillPersistsPortraitProjectAspect() {
        val image = MediaMetadata(
            uri = "file:///portrait-still.jpg",
            name = "portrait-still.jpg",
            durationMs = 3_000L,
            width = 1_920,
            height = 1_080,
            fps = 30,
            type = ClipType.IMAGE,
            rotationDegrees = 90,
        )

        val imported = importIntoEmptyProject(
            "portrait-still-project",
            listOf(image),
        )

        assertEquals(ClipType.IMAGE, imported.clips.single().type)
        assertEquals(9f / 16f, imported.videoAspectRatio!!, 0.001f)
    }

    private fun importIntoEmptyProject(projectId: String, media: List<MediaMetadata>): Project {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModelStore = ViewModelStore()
        try {
            val context = RuntimeEnvironment.getApplication()
            val initialProject = Project(id = projectId, name = projectId, durationMs = 0L)
            val vm = EditorViewModel(context = context, projectId = projectId).apply {
                _state.update { it.copy(project = initialProject, durationMs = 0L) }
            }
            viewModelStore.put(projectId, vm)
            vm.onMediaPicked(media)
            return runBlocking {
                withTimeout(5_000L) {
                    vm.state.first { state ->
                        state.project?.clips?.size == media.size && state.project?.videoAspectRatio != null
                    }.project!!
                }
            }
        } finally {
            viewModelStore.clear()
            Dispatchers.resetMain()
        }
    }
}
