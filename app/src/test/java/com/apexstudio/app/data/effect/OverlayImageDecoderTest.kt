package com.apexstudio.app.data.effect

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayImageDecoderTest {
    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private class TrackedStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun oddDimensionsRoundUpInsteadOfExceedingLimit() {
        assertEquals(4, OverlayImageDecoder.sampleSize(1025, 513, 512))
        assertEquals(2, OverlayImageDecoder.sampleSize(512, 513, 512))
        assertEquals(1, OverlayImageDecoder.sampleSize(512, 512, 512))
        assertEquals(1 shl 22, OverlayImageDecoder.sampleSize(Int.MAX_VALUE, 1, 512))
    }

    @Test fun invalidGeometryIsRejectedWithoutOverflow() {
        for (dimensions in listOf(Triple(0, 5, 512), Triple(5, -1, 512), Triple(5, 5, 0),
            Triple(Int.MAX_VALUE, 1, 1))) {
            assertThrows(IllegalArgumentException::class.java) {
                OverlayImageDecoder.sampleSize(dimensions.first, dimensions.second, dimensions.third)
            }
        }
    }

    @Test fun boundsAndPixelsUseSeparateClosedStreamsAndReturnOwnedBitmap() {
        val bytes = png(1025, 513)
        val streams = mutableListOf<TrackedStream>()
        val result = OverlayImageDecoder.decode { TrackedStream(bytes).also { streams.add(it) } }
        try {
            assertEquals(2, streams.size)
            assertTrue(streams.all { it.closed })
            assertTrue(result.width in 1..512)
            assertTrue(result.height in 1..512)
            assertFalse(result.isRecycled)
        } finally { result.recycle() }
    }

    @Test fun invalidImageClosesFirstStreamAndDoesNotDecodeAgain() {
        val stream = TrackedStream(byteArrayOf(1, 2, 3))
        var opens = 0
        assertThrows(IllegalArgumentException::class.java) {
            OverlayImageDecoder.decode { opens++; stream }
        }
        assertEquals(1, opens)
        assertTrue(stream.closed)
    }

    @Test fun failureReopeningDoesNotRetainBoundsStream() {
        val stream = TrackedStream(png(4, 3))
        var opens = 0
        assertThrows(IOException::class.java) {
            OverlayImageDecoder.decode {
                if (++opens == 1) stream else throw IOException("Provider no longer available")
            }
        }
        assertEquals(2, opens)
        assertTrue(stream.closed)
    }

    @Test fun failedPixelDecodeClosesBothStreams() {
        val bounds = TrackedStream(png(4, 3))
        val badPixels = TrackedStream(byteArrayOf(0, 1))
        var opens = 0
        assertThrows(IllegalStateException::class.java) {
            OverlayImageDecoder.decode { if (++opens == 1) bounds else badPixels }
        }
        assertTrue(bounds.closed)
        assertTrue(badPixels.closed)
    }

    @Test fun privateFilePathsAndFileUrisBothDecode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("overlay-image", ".png", context.cacheDir)
        try {
            file.writeBytes(png(8, 5))
            for (source in listOf(file.absolutePath, file.toURI().toString())) {
                val decoded = OverlayImageDecoder.decode(context, source)
                try { assertEquals(8, decoded.width); assertEquals(5, decoded.height) }
                finally { decoded.recycle() }
            }
        } finally { file.delete() }
    }
}
