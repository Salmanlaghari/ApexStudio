package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
}
