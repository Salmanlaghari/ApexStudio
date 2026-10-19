package com.apexstudio.app.data.media

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimelineThumbnailCacheTest {
    @Test
    fun `image-backed overlay is decoded through photo renderer`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val imageFile = File(context.cacheDir, "overlay-still-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        try {
            imageFile.outputStream().use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            source.recycle()
        }

        val clip = MediaClip(
            id = "image-overlay",
            name = imageFile.name,
            uri = Uri.fromFile(imageFile).toString(),
            durationMs = 5_000L,
            trimEndMs = 5_000L,
            type = ClipType.OVERLAY,
        )
        val frame = TimelineThumbnailCache.frameFor(context, clip, 0L, 32, 16)
        try {
            assertNotNull("still OVERLAY should use the image decoder", frame)
            val rendered = frame ?: error("still OVERLAY should use the image decoder")
            assertEquals(32, rendered.width)
            assertEquals(16, rendered.height)
            assertEquals(Color.MAGENTA, rendered.getPixel(16, 8))
        } finally {
            frame?.recycle()
            imageFile.delete()
        }
    }

    @Test
    fun `overlay video falls through when photo decode fails`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = TimelineThumbnailCache.imageFirstFallback(
            type = ClipType.OVERLAY,
            imageDecode = {
                calls += "photo"
                null
            },
            videoDecode = {
                calls += "retriever"
                "decoded video frame"
            },
        )

        assertEquals("decoded video frame", result)
        assertEquals(listOf("photo", "retriever"), calls)
    }
}
