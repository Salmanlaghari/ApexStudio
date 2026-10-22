package com.apexstudio.app.data.media

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaUriResolverTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun missingNamedProjectMediaIsNotMaterializedOrReplacedWithSample() {
        val name = "missing-owner-video-${System.nanoTime()}.mp4"
        val result = MediaUriResolver.resolvePlayableUri(context, name)
        assertEquals(Uri.fromFile(File(context.filesDir, name)), result)
        assertFalse(File(context.filesDir, name).exists())
        assertFalse(File(context.cacheDir, name).exists())
        assertFalse(result.toString().contains("apex_sample"))
    }

    @Test fun missingAbsoluteSourceDoesNotResolveToSameNamedCacheFile() {
        val cache = File(context.cacheDir, "owner-source.mp4").apply { writeText("unrelated") }
        try {
            val source = "file:///unavailable/owner-source.mp4"
            assertEquals(Uri.parse(source), MediaUriResolver.resolvePlayableUri(context, source))
        } finally { cache.delete() }
    }

    @Test fun blankSourceDoesNotGenerateSampleAndValidFilesRemainUnchanged() {
        assertEquals(Uri.EMPTY, MediaUriResolver.resolvePlayableUri(context, null))
        val source = File(context.filesDir, "real-owner-source.mp4").apply { writeText("original-media") }
        try {
            assertEquals(Uri.fromFile(source), MediaUriResolver.resolvePlayableUri(context, source.absolutePath))
            assertEquals("original-media", source.readText())
        } finally { source.delete() }
    }

    @Test fun inaccessibleContentUriRemainsOriginalRatherThanSample() {
        val source = "content://unavailable-owner-provider/${System.nanoTime()}"
        assertEquals(Uri.parse(source), MediaUriResolver.resolvePlayableUri(context, source))
    }
}
