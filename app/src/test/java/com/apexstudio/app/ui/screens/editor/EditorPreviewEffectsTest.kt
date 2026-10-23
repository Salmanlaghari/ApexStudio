package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.Effect
import com.apexstudio.app.domain.model.AnimatedTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPreviewEffectsTest {
    @Test
    fun `preview merge preserves exported effect ordering`() {
        val crop = object : Effect {}
        val workspace = object : Effect {}
        val mask = object : Effect {}
        val lut = object : Effect {}
        val fxAndKeyframes = object : Effect {}

        assertEquals(
            listOf(crop, workspace, mask, lut, fxAndKeyframes),
            mergeEditorPreviewEffects(
                geometryAndWorkspaceEffects = listOf(crop, workspace, mask),
                editorGradeEffects = listOf(lut, fxAndKeyframes),
            ),
        )
    }

    @Test
    fun `empty editor grade chain keeps crop and workspace chain intact`() {
        val crop = object : Effect {}
        assertEquals(
            listOf(crop),
            mergeEditorPreviewEffects(listOf(crop), emptyList()),
        )
    }

    @Test
    fun `Compose grade remains only for stills including during video LUT comparison`() {
        assertEquals(true, shouldRenderComposeGrade(isPhotoClip = true, lutCompareMode = false))
        assertEquals(false, shouldRenderComposeGrade(isPhotoClip = false, lutCompareMode = true))
        assertEquals(false, shouldRenderComposeGrade(isPhotoClip = false, lutCompareMode = false))
    }

    @Test
    fun `Compose FX are retained for photos but not actual video`() {
        assertEquals(true, shouldRenderComposeFx(isPhotoClip = true))
        assertEquals(false, shouldRenderComposeFx(isPhotoClip = false))
    }

    @Test
    fun `video keyframe transform is not applied a second time in Compose`() {
        val transform = AnimatedTransform(
            translateX = 0f,
            translateY = 0f,
            scale = 1.5f,
            rotationDeg = 20f,
            opacity = 1f,
        )
        assertEquals(AnimatedTransform.Identity, composeTransformForPreview(false, transform))
        assertEquals(transform, composeTransformForPreview(true, transform))
    }
}
