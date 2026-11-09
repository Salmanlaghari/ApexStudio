package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorPhotoEnhanceControllerTest {
    private fun editor(): EditorViewModel {
        val clip = MediaClip("photo", "Photo", "file:///photo.jpg", 1_000L, trimEndMs = 1_000L, type = ClipType.IMAGE)
        return EditorViewModel().also {
            it._state.value = it._state.value.copy(
                project = Project("project", "Project", 1_000L, clips = listOf(clip)),
                selectedClipId = clip.id
            )
        }
    }

    @Test fun autoEnhanceUsesExistingLocalPhotoAdjustmentPipeline() {
        val vm = editor()
        vm.applyPhotoAutoEnhance("photo")
        val adjustments = vm.state.value.project!!.clips.single().photoEdit.adjustments
        assertEquals(0.08f, adjustments.brightness, 0.0001f)
        assertEquals(1.08f, adjustments.contrast, 0.0001f)
        assertEquals(1.05f, adjustments.saturation, 0.0001f)
        assertEquals(0.12f, adjustments.clarity, 0.0001f)
        assertEquals(0.18f, adjustments.sharpness, 0.0001f)
    }

    @Test fun denoiseAndSharpenAreRealAndIdempotent() {
        val vm = editor()
        vm.applyPhotoDenoise("photo")
        vm.applyPhotoSharpen("photo")
        vm.applyPhotoDenoise("photo")
        vm.applyPhotoSharpen("photo")
        val adjustments = vm.state.value.project!!.clips.single().photoEdit.adjustments
        assertEquals(0.35f, adjustments.denoise, 0.0001f)
        assertEquals(0.35f, adjustments.sharpness, 0.0001f)
        assertTrue(vm.state.value.project!!.clips.single().photoEdit.adjustments.hasNonMatrixAdjustments)
    }
}
