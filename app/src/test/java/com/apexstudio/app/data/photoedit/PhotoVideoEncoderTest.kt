package com.apexstudio.app.data.photoedit

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoVideoEncoderTest {

    @Test
    fun `argbToNv12 writes Y plane followed by interleaved U then V`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)

            val yuv = PhotoVideoEncoder.argbToNv12(bitmap, 2, 2)

            assertEquals(6, yuv.size)
            assertArrayEquals(byteArrayOf(82, 82, 82, 82, 90, 240.toByte()), yuv)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `argbToNv12 rejects odd dimensions and undersized bitmaps`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(runCatching { PhotoVideoEncoder.argbToNv12(bitmap, 1, 2) }.isFailure)
            assertTrue(runCatching { PhotoVideoEncoder.argbToNv12(bitmap, 2, 1) }.isFailure)
            assertTrue(runCatching { PhotoVideoEncoder.argbToNv12(bitmap, 4, 2) }.isFailure)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `duration frame count rounds up without enforcing a one second minimum`() {
        assertEquals(1, PhotoVideoEncoder.frameCountForDurationMs(1))
        assertEquals(1, PhotoVideoEncoder.frameCountForDurationMs(99))
        assertEquals(1, PhotoVideoEncoder.frameCountForDurationMs(100))
        assertEquals(2, PhotoVideoEncoder.frameCountForDurationMs(101))
        assertEquals(10, PhotoVideoEncoder.frameCountForDurationMs(999))
        assertEquals(10, PhotoVideoEncoder.frameCountForDurationMs(1_000))
        assertEquals(11, PhotoVideoEncoder.frameCountForDurationMs(1_001))
    }

    @Test
    fun `duration frame arithmetic rejects invalid and excessively large durations`() {
        assertNull(PhotoVideoEncoder.frameCountForDurationMs(Long.MIN_VALUE))
        assertNull(PhotoVideoEncoder.frameCountForDurationMs(-1))
        assertNull(PhotoVideoEncoder.frameCountForDurationMs(0))
        assertEquals(864_000, PhotoVideoEncoder.frameCountForDurationMs(86_400_000L))
        assertNull(PhotoVideoEncoder.frameCountForDurationMs(86_400_001L))
        assertNull(PhotoVideoEncoder.frameCountForDurationMs(Long.MAX_VALUE))
    }
}
