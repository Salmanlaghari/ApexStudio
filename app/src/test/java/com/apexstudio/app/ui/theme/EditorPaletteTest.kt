package com.apexstudio.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPaletteTest {
    @Test
    fun semanticAccentsStatusAndMediaTrackColorsRemainStable() {
        assertEquals(ApexPalette.NeonEmerald, EditorPalette.NeonEmerald)
        assertEquals(ApexPalette.NeonPink, EditorPalette.NeonPink)
        assertEquals(ApexPalette.NeonAmber, EditorPalette.NeonAmber)
        assertEquals(ApexPalette.TrackBlue, EditorPalette.TrackBlue)
        assertEquals(ApexPalette.Success, EditorPalette.Success)
        assertEquals(ApexPalette.Warning, EditorPalette.Warning)
        assertEquals(ApexPalette.Danger, EditorPalette.Danger)
        assertEquals(ApexPalette.TrackVideo, EditorPalette.TrackVideo)
        assertEquals(ApexPalette.TrackAudio, EditorPalette.TrackAudio)
        assertEquals(ApexPalette.TrackOverlay, EditorPalette.TrackOverlay)
        assertEquals(ApexPalette.TrackSfx, EditorPalette.TrackSfx)
    }
}
