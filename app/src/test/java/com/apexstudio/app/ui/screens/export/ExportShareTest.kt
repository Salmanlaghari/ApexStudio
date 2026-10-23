package com.apexstudio.app.ui.screens.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportShareTest {
    @Before fun resetProviderRootsBetweenRobolectricSandboxes() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        cache.isAccessible = true
        (cache.get(null) as MutableMap<*, *>).clear()
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun output(name: String) = File(context.getExternalFilesDir(null), "ApexStudio_Exports/$name").also {
        it.parentFile!!.mkdirs(); it.writeBytes(byteArrayOf(1, 2, 3))
    }
    @Test fun completedVideoUsesReadOnlyContentUriAndClipData() {
        val file = output("share.mp4")
        val intent = completedExportShareIntent(context, Uri.fromFile(file).toString(), "#OneTake")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("video/mp4", intent.type)
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals("#OneTake", intent.getStringExtra(Intent.EXTRA_TEXT))
    }
    @Test fun audioExportHasCorrectMimeType() {
        val file = output("share.m4a")
        assertEquals("audio/mp4", completedExportShareIntent(context, Uri.fromFile(file).toString()).type)
    }
    @Test fun rejectsMissingEmptyExternalOrUnfinishedFiles() {
        val outside = File(context.cacheDir, "private.mp4").apply { writeText("private") }
        val empty = output("empty.mp4").apply { writeBytes(byteArrayOf()) }
        val partial = output("partial.tmp")
        listOf(Uri.fromFile(outside).toString(), Uri.fromFile(empty).toString(), Uri.fromFile(partial).toString(),
            "file:///missing.mp4", "https://example.com/clip.mp4", "content://other/clip").forEach {
            assertTrue(it, runCatching { completedExportShareIntent(context, it) }.isFailure)
        }
    }
}
