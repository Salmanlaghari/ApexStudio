package com.apexstudio.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPaletteTest {
    @Test
    fun editorPaletteUsesLightSurfacesAndDarkText() {
        assertEquals(Color.White, EditorPalette.BgBase)
        assertEquals(Color.White, EditorPalette.BgSurface)
        assertTrue(EditorPalette.TextPrimary.luminance() < EditorPalette.BgBase.luminance())
    }

    @Test
    fun editorPalettePreservesSemanticAccentsAndMediaTrackColors() {
        assertEquals(ApexPalette.NeonCyan, EditorPalette.NeonCyan)
        assertEquals(ApexPalette.NeonPurple, EditorPalette.NeonPurple)
        assertEquals(ApexPalette.NeonPink, EditorPalette.NeonPink)
        assertEquals(ApexPalette.NeonAmber, EditorPalette.NeonAmber)
        assertEquals(ApexPalette.Success, EditorPalette.Success)
        assertEquals(ApexPalette.Warning, EditorPalette.Warning)
        assertEquals(ApexPalette.Danger, EditorPalette.Danger)
        assertEquals(ApexPalette.TrackVideo, EditorPalette.TrackVideo)
        assertEquals(ApexPalette.TrackAudio, EditorPalette.TrackAudio)
        assertEquals(ApexPalette.TrackOverlay, EditorPalette.TrackOverlay)
        assertEquals(ApexPalette.TrackSfx, EditorPalette.TrackSfx)
    }
}
