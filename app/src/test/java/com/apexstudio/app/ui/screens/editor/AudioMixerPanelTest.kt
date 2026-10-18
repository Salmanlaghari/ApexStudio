package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMixerPanelTest {
    @Test
    fun trackKindLabelDoesNotDependOnFaderVolume() {
        assertEquals("Music", audioTrackKindLabel(AudioTrack.Kind.MUSIC))
        assertEquals("Voiceover / SFX", audioTrackKindLabel(AudioTrack.Kind.SFX))
        assertEquals("Original video", audioTrackKindLabel(AudioTrack.Kind.ORIGINAL_VIDEO))
    }
}
