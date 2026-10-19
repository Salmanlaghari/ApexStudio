package com.apexstudio.app.data.effect

import org.junit.Assert.*
import org.junit.Test

class CropSourceDimensionsTest {
    @Test fun portraitRotationUsesDisplayedAspectNotEncodedLandscapeAspect() {
        val source = CropSourceDimensions.oriented(4032, 3024, 90)!!
        assertEquals(3024, source.width)
        assertEquals(4032, source.height)
        assertEquals(.75f, source.aspect, .00001f)
        val square = VideoCropPreset.SQUARE.centeredWindow(source.aspect)
        assertEquals(1f, square.width * source.aspect / square.height, .00001f)
    }
    @Test fun negativeAndMultipleTurnsAreNormalizedBeforeAxisSwap() {
        assertEquals(CropSourceDimensions(1080, 1920), CropSourceDimensions.oriented(1920, 1080, -90))
        assertEquals(CropSourceDimensions(1080, 1920), CropSourceDimensions.oriented(1920, 1080, 450))
        assertEquals(CropSourceDimensions(1920, 1080), CropSourceDimensions.oriented(1920, 1080, 540))
        // EXIF 90 degrees followed by a user photo quarter-turn restores the original axes.
        assertEquals(CropSourceDimensions(4032, 3024), CropSourceDimensions.oriented(4032, 3024, 90 + 90))
    }
    @Test fun invalidMetadataNeverPretendsToBeSquare() {
        for (width in listOf(-1, 0, 1920)) for (height in listOf(-1, 0, 1080)) {
            if (width <= 0 || height <= 0) assertNull(CropSourceDimensions.oriented(width, height, 0))
        }
    }
    @Test fun allPresetsRetainDisplayedTargetAfterEveryQuarterTurn() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val source = CropSourceDimensions.oriented(4032, 3024, rotation)!!
            for (preset in VideoCropPreset.all) preset.aspectRatio?.let { target ->
                val window = preset.centeredWindow(source.aspect)
                assertEquals(target, window.width * source.aspect / window.height, .00001f)
            }
        }
    }
}
