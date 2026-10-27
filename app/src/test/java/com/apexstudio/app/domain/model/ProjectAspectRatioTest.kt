package com.apexstudio.app.domain.model

import com.apexstudio.app.data.picker.MediaMetadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectAspectRatioTest {
    @Test
    fun videoDimensionsKeepTheirActualOrientation() {
        assertEquals(9f / 16f, ProjectAspectRatio.fromDimensions(1080, 1920)!!, 0.0001f)
        assertEquals(16f / 9f, ProjectAspectRatio.fromDimensions(1920, 1080)!!, 0.0001f)
        assertEquals(9f / 16f, ProjectAspectRatio.fromDimensions(1920, 1080, rotationDegrees = 90)!!, 0.0001f)
        assertEquals(16f / 9f, ProjectAspectRatio.fromDimensions(1080, 1920, rotationDegrees = -90)!!, 0.0001f)
    }

    @Test
    fun invalidDimensionsAndRatiosDoNotCreateDefaults() {
        assertNull(ProjectAspectRatio.fromDimensions(0, 1920))
        assertNull(ProjectAspectRatio.fromDimensions(1920, -1))
        assertNull(ProjectAspectRatio.exportPreset(Float.NaN))
        assertNull(ProjectAspectRatio.exportPreset(null))
    }

    @Test
    fun sourceAspectMapsToTheNearestSupportedExportPreset() {
        assertEquals("9:16", ProjectAspectRatio.exportPreset(9f / 16f))
        assertEquals("16:9", ProjectAspectRatio.exportPreset(16f / 9f))
        assertEquals("1:1", ProjectAspectRatio.exportPreset(1f))
    }

    @Test
    fun mediaMetadataUsesRotationForTheDisplayedVideoAspect() {
        val metadata = MediaMetadata(
            uri = "file:///portrait.mp4",
            name = "portrait.mp4",
            durationMs = 1_000L,
            width = 1920,
            height = 1080,
            fps = 30,
            type = ClipType.VIDEO,
            rotationDegrees = 90
        )
        assertEquals(9f / 16f, metadata.sourceVideoAspectRatio!!, 0.0001f)
    }
}
