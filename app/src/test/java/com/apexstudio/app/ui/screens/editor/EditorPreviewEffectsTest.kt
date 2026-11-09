package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.Effect
import com.apexstudio.app.domain.model.GpuFilterConfig
import com.apexstudio.app.domain.model.StylisticEffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPreviewEffectsTest {
    @Test
    fun `player graph keeps geometry and workspace before editor grades`() {
        val crop = object : Effect {}
        val workspace = object : Effect {}
        val lut = object : Effect {}
        val adjustment = object : Effect {}
        val fx = object : Effect {}

        val merged = mergeEditorPreviewEffects(
            geometryAndWorkspaceEffects = listOf(crop, workspace),
            editorGradeEffects = listOf(lut, adjustment, fx),
        )

        assertEquals(5, merged.size)
        assertSame(crop, merged[0])
        assertSame(workspace, merged[1])
        assertSame(lut, merged[2])
        assertSame(adjustment, merged[3])
        assertSame(fx, merged[4])
    }

    @Test
    fun `compose grade and fx fallbacks are limited to photos and explicit compare`() {
        assertTrue(shouldRenderComposeGrade(isPhotoClip = true, lutCompareMode = false))
        assertTrue(shouldRenderComposeGrade(isPhotoClip = false, lutCompareMode = true))
        assertFalse(shouldRenderComposeGrade(isPhotoClip = false, lutCompareMode = false))
        assertTrue(shouldRenderComposeFx(isPhotoClip = true))
        assertFalse(shouldRenderComposeFx(isPhotoClip = false))
    }

    @Test
    fun `gpu configs use media3 only when their active operations match export`() {
        val supported = GpuFilterConfig(brightness = 0.2f, saturation = 1.3f)
        assertTrue(gpuFilterConfigUsesMedia3(supported))
        assertFalse(gpuFilterConfigUsesMedia3(GpuFilterConfig(gamma = 1.2f)))
        assertFalse(gpuFilterConfigUsesMedia3(GpuFilterConfig(
            stylisticEffect = StylisticEffectType.HALFTONE
        )))
        assertFalse(gpuFilterConfigUsesMedia3(GpuFilterConfig(activeProfileId = "film")))

        assertFalse(shouldRenderComposeGpuFilter(false, false, supported))
        assertTrue(shouldRenderComposeGpuFilter(false, true, supported))
        assertTrue(shouldRenderComposeGpuFilter(true, false, supported))
    }

    @Test
    fun `preview frame follows decoded source ratio and gently enlarges portrait media`() {
        val fallback = 16f / 9f
        assertEquals(9f / 16f, editorPreviewStageAspectRatio(9f / 16f, fallback), 0.0001f)
        assertEquals(fallback, editorPreviewStageAspectRatio(Float.NaN, fallback), 0.0001f)
        assertEquals(1.05f, portraitPreviewScale(9f / 16f), 0.0001f)
        assertEquals(1f, portraitPreviewScale(16f / 9f), 0.0001f)
        assertEquals(1f, portraitPreviewScale(null), 0.0001f)
    }
}
