package com.apexstudio.app.ai

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [OfflineVideoFrameDecoderTest.ShadowRetriever::class])
class OfflineVideoFrameDecoderTest {
    @Test
    fun api27UsesScaledDecoderAndRetainsRotatedFullSourceGeometryAndCallerOwnership() {
        ShadowRetriever.resetState()
        ShadowRetriever.metadata = validMetadata(width = 3840, height = 2160, rotation = 90)
        val scaledResult = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        ShadowRetriever.scaledResult = scaledResult

        val decoded = OfflineVideoFrameDecoder.decode(MediaMetadataRetriever(), 1_250_000L, 640)

        assertEquals(1, ShadowRetriever.scaledCalls)
        assertEquals(0, ShadowRetriever.fullCalls)
        assertEquals(360 to 640, ShadowRetriever.requestedSize)
        assertEquals(2160, decoded!!.sourceWidth)
        assertEquals(3840, decoded.sourceHeight)
        assertSame(scaledResult, decoded.bitmap)
        assertFalse(decoded.bitmap.isRecycled)
        decoded.bitmap.recycle()
        assertTrue(scaledResult.isRecycled)
    }

    @Test
    @Config(sdk = [26])
    fun api26UsesFullFrameThenScalesAndRecyclesTheIntermediateBitmap() {
        ShadowRetriever.resetState()
        ShadowRetriever.metadata = validMetadata(width = 1920, height = 1080, rotation = 0)
        val fullResolution = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        ShadowRetriever.fullResult = fullResolution

        val decoded = OfflineVideoFrameDecoder.decode(MediaMetadataRetriever(), 250_000L, 640)

        assertEquals(0, ShadowRetriever.scaledCalls)
        assertEquals(1, ShadowRetriever.fullCalls)
        assertEquals(1920, decoded!!.sourceWidth)
        assertEquals(1080, decoded.sourceHeight)
        assertEquals(640, decoded.bitmap.width)
        assertEquals(360, decoded.bitmap.height)
        assertTrue("full-resolution intermediate should be released", fullResolution.isRecycled)
        assertFalse(decoded.bitmap.isRecycled)
        decoded.bitmap.recycle()
    }

    @Test
    fun malformedOrUnavailableMetadataUsesLegacyDecodeAndUsesItsDimensionsAsBestAvailableGeometry() {
        ShadowRetriever.resetState()
        ShadowRetriever.metadata = mapOf(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH to "n/a",
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT to "2160",
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION to "0",
        )
        val fullResolution = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        ShadowRetriever.fullResult = fullResolution

        val decoded = OfflineVideoFrameDecoder.decode(MediaMetadataRetriever(), 0L, 640)

        assertEquals(0, ShadowRetriever.scaledCalls)
        assertEquals(1, ShadowRetriever.fullCalls)
        assertEquals(1280, decoded!!.sourceWidth)
        assertEquals(720, decoded.sourceHeight)
        assertEquals(640, decoded.bitmap.width)
        assertEquals(360, decoded.bitmap.height)
        assertTrue(fullResolution.isRecycled)
        decoded.bitmap.recycle()
    }

    @Test
    fun scaledDecodeFailureDoesNotFallBackToAnUnboundedFullResolutionDecode() {
        ShadowRetriever.resetState()
        ShadowRetriever.metadata = validMetadata(3840, 2160, 0)
        ShadowRetriever.scaledResult = null

        assertNull(OfflineVideoFrameDecoder.decode(MediaMetadataRetriever(), 0L, 640))
        assertEquals(1, ShadowRetriever.scaledCalls)
        assertEquals(0, ShadowRetriever.fullCalls)
    }

    @Test
    fun legacyDecodeErrorsPropagateWithoutHidingTheFailure() {
        ShadowRetriever.resetState()
        ShadowRetriever.metadata = emptyMap()
        ShadowRetriever.fullDecodeFailure = IllegalStateException("decoder failed")

        try {
            OfflineVideoFrameDecoder.decode(MediaMetadataRetriever(), 0L, 640)
            throw AssertionError("Expected decoder failure")
        } catch (expected: IllegalStateException) {
            assertEquals("decoder failed", expected.message)
        }
        assertEquals(1, ShadowRetriever.fullCalls)
    }

    private fun validMetadata(width: Int, height: Int, rotation: Int) = mapOf(
        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH to width.toString(),
        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT to height.toString(),
        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION to rotation.toString(),
    )

    @Implements(MediaMetadataRetriever::class)
    class ShadowRetriever {
        @Implementation
        fun extractMetadata(keyCode: Int): String? = metadata[keyCode]

        @Implementation
        fun getFrameAtTime(timeUs: Long, option: Int): Bitmap? {
            fullCalls++
            fullDecodeFailure?.let { throw it }
            return fullResult
        }

        @Implementation(minSdk = 27)
        fun getScaledFrameAtTime(timeUs: Long, option: Int, dstWidth: Int, dstHeight: Int): Bitmap? {
            scaledCalls++
            requestedSize = dstWidth to dstHeight
            return scaledResult
        }

        companion object {
            var metadata: Map<Int, String> = emptyMap()
            var fullResult: Bitmap? = null
            var scaledResult: Bitmap? = null
            var fullDecodeFailure: RuntimeException? = null
            var fullCalls: Int = 0
            var scaledCalls: Int = 0
            var requestedSize: Pair<Int, Int>? = null

            @Resetter
            @JvmStatic
            fun reset() = resetState()

            fun resetState() {
                metadata = emptyMap()
                fullResult = null
                scaledResult = null
                fullDecodeFailure = null
                fullCalls = 0
                scaledCalls = 0
                requestedSize = null
            }
        }
    }
}
