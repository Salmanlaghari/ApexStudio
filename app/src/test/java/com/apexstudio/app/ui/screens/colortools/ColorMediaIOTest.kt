package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMediaIOTest {
    @Test fun sourcePickerOnlyOffersExistingProjectVideosAndImages() {
        val image = clip("photo", ClipType.IMAGE)
        val video = clip("movie", ClipType.VIDEO)
        val audio = clip("sound", ClipType.AUDIO)
        val overlay = clip("overlay", ClipType.OVERLAY)
        assertEquals(listOf(image, video), ColorMediaIO.selectedSources(listOf(image, audio, video, overlay)))
    }

    @Test fun previewTimeIsBoundedToTheSelectedClipTrimAndSourceDuration() {
        val selected = clip("movie", ClipType.VIDEO).copy(durationMs = 9_000, trimStartMs = 2_000, trimEndMs = 7_000)
        assertEquals(2_000L, ColorMediaIO.previewTimeMs(selected, -100))
        assertEquals(4_500L, ColorMediaIO.previewTimeMs(selected, 4_500))
        assertEquals(7_000L, ColorMediaIO.previewTimeMs(selected, 99_000))
        // Stale project trim metadata must not seek beyond the duration verified from the source.
        assertEquals(4_000L, ColorMediaIO.previewTimeMs(selected, 99_000, sourceDurationMs = 4_000))
        assertEquals(4_000L, ColorMediaIO.previewTimeMs(selected.copy(trimStartMs = 7_000), 99_000,
            sourceDurationMs = 4_000))
    }

    @Test fun frameSeekUsesSaturatingMillisecondToMicrosecondConversion() {
        assertEquals(0L, ColorMediaIO.timeMsToMicros(-10L))
        assertEquals(1_234_000L, ColorMediaIO.timeMsToMicros(1_234L))
        assertEquals(Long.MAX_VALUE, ColorMediaIO.timeMsToMicros(Long.MAX_VALUE))
    }

    @Test fun standaloneColorUsesUntouchedOriginalWhenClipHasBackgroundReplacement() {
        val clip = clip("movie", ClipType.VIDEO).copy(uri = "file:///processed.mp4",
            backgroundReplace = BackgroundReplaceSettings(sourceUri = "file:///original.mp4"))
        assertEquals("file:///original.mp4", ColorMediaIO.sourceUri(clip))
        assertEquals("file:///processed.mp4", ColorMediaIO.sourceUri(clip.copy(
            backgroundReplace = BackgroundReplaceSettings(sourceUri = " "))))
    }

    @Test fun previewBoundsNeverUpscaleAndKeepAspectWithinLimit() {
        assertEquals(1280 to 720, ColorMediaIO.boundedDimensions(3840, 2160, 1280))
        assertEquals(640 to 480, ColorMediaIO.boundedDimensions(640, 480, 1280))
    }

    @Test fun beforeAfterDividerUsesOneClampedCoordinateAcrossBothLayers() {
        val widthPx = 900f
        val position = colorPreviewSplitPosition(widthPx, .37f)
        // The before clip and after overlay share this single canvas-space divider position.
        assertEquals(333f, position, .001f)
        assertEquals(0f, colorPreviewSplitPosition(widthPx, -2f), .001f)
        assertEquals(widthPx, colorPreviewSplitPosition(widthPx, 2f), .001f)
    }

    @Test fun stillExportCapAcceptsSafeSourcesAndRefusesOversizedOnes() {
        assertFalse(ColorMediaIO.stillExportAllowed(6000, 5000))
        assertTrue(ColorMediaIO.stillExportAllowed(4000, 3000))
        assertFalse(ColorMediaIO.stillExportAllowed(9000, 4000))
        assertFalse(ColorMediaIO.stillExportAllowed(8192, 5000))
        assertFalse(ColorMediaIO.stillExportAllowed(0, 100))
    }

    private fun clip(id: String, type: ClipType) = MediaClip(
        id = id, name = id, uri = "file:///media/$id", durationMs = 8_000,
        trimStartMs = 0, trimEndMs = 8_000, type = type
    )
}
