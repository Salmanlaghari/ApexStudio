package com.apexstudio.app.data.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AR Face cards: catalog integrity + preview/export parity.
 *
 * Every preset in [ArFilterCatalog] must have a matching GLSL shader in
 * [ArFaceGlEffect] (see [ArFaceGlEffect.isKnownFilter]); otherwise the
 * export would silently drop a filter the user previewed. This test is
 * the guard that keeps the preview renderer
 * ([com.apexstudio.app.ui.screens.editor.ArFaceFilterOverlay]) and the
 * export effect in lockstep when presets are added.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArFaceFilterTest {

    @Test
    fun catalog_hasThirteenPresetsWithUniqueIds() {
        val filters = ArFilterCatalog.FILTERS
        assertEquals(13, filters.size)
        assertEquals(filters.size, filters.map { it.id }.toSet().size)
    }

    @Test
    fun newPresets_existWithExpectedCategories() {
        val warmGlow = ArFilterCatalog.getFilterById("ar_warm_glow")
        assertNotNull(warmGlow)
        assertEquals(ArFilterCategory.BEAUTY, warmGlow!!.category)

        val zoomPulse = ArFilterCatalog.getFilterById("ar_face_zoom_pulse")
        assertNotNull(zoomPulse)
        assertEquals(ArFilterCategory.FACE_EYES, zoomPulse!!.category)
    }

    @Test
    fun everyCatalogPreset_hasMatchingExportShader() {
        ArFilterCatalog.FILTERS.forEach { preset ->
            assertTrue(
                "No export shader for AR preset ${preset.id}",
                ArFaceGlEffect.isKnownFilter(preset.id)
            )
        }
    }

    @Test
    fun unknownId_isNotKnownFilter() {
        assertFalse(ArFaceGlEffect.isKnownFilter("ar_does_not_exist"))
        assertFalse(ArFaceGlEffect.isKnownFilter(null))
    }
}
