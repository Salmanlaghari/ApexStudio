package com.apexstudio.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPaletteTest {
    @Test
    fun editorPaletteKeepsDarkPr170SurfacesWithOnlyASubtleNeutralLift() {
        assertTrue(EditorPalette.BgBase.luminance() < 0.03f)
        assertTrue(EditorPalette.BgSurface.luminance() < 0.04f)
        assertTrue(EditorPalette.BgSurface != Color.White)
        assertTrue(EditorPalette.BgDeep.luminance() < EditorPalette.BgBase.luminance())
        assertTrue(EditorPalette.BgSurface.luminance() > EditorPalette.BgBase.luminance())
        assertTrue(EditorPalette.TextPrimary.luminance() > EditorPalette.BgBase.luminance())
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
