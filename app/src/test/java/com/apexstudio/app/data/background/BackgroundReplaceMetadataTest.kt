package com.apexstudio.app.data.background

import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundReplaceMetadataTest {
    @Test
    fun `BT709 full-range metadata selects its matrix`() {
        val format = MediaFormat().apply {
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
        }

        val params = BackgroundReplaceEngine.yuvParamsFor(format)
        assertEquals(0, params.yOffset)
        assertEquals(256, params.yScale)
        assertEquals(403, params.rV)
        assertEquals(-48, params.gU)
        assertEquals(-120, params.gV)
        assertEquals(475, params.bU)
    }

    @Test
    fun `BT2020 limited-range metadata selects its matrix`() {
        val format = MediaFormat().apply {
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
        }

        val params = BackgroundReplaceEngine.yuvParamsFor(format)
        assertEquals(16, params.yOffset)
        assertEquals(298, params.yScale)
        assertEquals(430, params.rV)
        assertEquals(-48, params.gU)
        assertEquals(-166, params.gV)
        assertEquals(548, params.bU)
    }

    @Test
    fun `missing color metadata keeps the documented BT601 limited fallback`() {
        val params = BackgroundReplaceEngine.yuvParamsFor(MediaFormat())
        assertEquals(16, params.yOffset)
        assertEquals(298, params.yScale)
        assertEquals(409, params.rV)
        assertEquals(-100, params.gU)
        assertEquals(-208, params.gV)
        assertEquals(516, params.bU)
    }

    @Test
    fun `rotation parser accepts absent and supported right-angle metadata`() {
        assertEquals(0, BackgroundReplaceEngine.parseRotationMetadata(null))
        assertEquals(0, BackgroundReplaceEngine.parseRotationMetadata("0"))
        assertEquals(90, BackgroundReplaceEngine.parseRotationMetadata("90"))
        assertEquals(180, BackgroundReplaceEngine.parseRotationMetadata("180"))
        assertEquals(270, BackgroundReplaceEngine.parseRotationMetadata("270"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rotation parser rejects invalid values instead of assuming zero`() {
        BackgroundReplaceEngine.parseRotationMetadata("45")
    }
}
