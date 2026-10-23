package com.apexstudio.app.ui.screens.colortools

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Requires a real Android codec/GL stack. Compilation alone is NOT a passing device test. */
@RunWith(AndroidJUnit4::class)
class ColorTimelineComparisonInstrumentedTest {
    @Test fun twoClipTrimSpeedTextAndRangedLookCompareTheActualExportWithoutChangingSources() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File.createTempFile("color-timeline-source-", ".mp4", context.cacheDir)
        var comparison: ColorTimelineComparisonRenderer.Comparison? = null
        try {
            instrumentation.context.assets.open("color_timeline/sdr_fixture.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            val originalHash = sha256(source)
            val first = MediaClip("first", "First", Uri.fromFile(source).toString(), 1000,
                trimStartMs = 100, trimEndMs = 900,
                textOverlays = listOf(TextOverlay("title-a", "Timeline title", startMs = 200, endMs = 700)),
                workspaceEffects = listOf(WorkspaceEffect(WorkspaceEffectPresets.WARM, 300, 600, intensity = .7f)))
            val second = first.copy(id = "second", name = "Second", speedMultiplier = 2f,
                textOverlays = listOf(TextOverlay("title-b", "Second clip", startMs = 0, endMs = 400)))
            val result = ColorTimelineComparisonRenderer.render(context, listOf(first, second),
                ExportEngine.ExportConfig(resolution = "720P", fps = 30, aspectRatio = "16:9",
                    colorStudioGrade = ColorStudioGrade(brightness = .25f, saturation = .6f))) {}
            comparison = result
            assertTrue(result.before.isFile && result.after.isFile)
            assertNotEquals(result.before.canonicalPath, result.after.canonicalPath)
            val afterHash = sha256(result.after)
            // Both clips must be represented, including the sped-up second clip.
            assertTrue("Expected 800ms + 400ms composition", result.durationMs in 1100L..1300L)
            for (atMs in listOf(100L, 450L, 950L)) {
                val pair = ColorTimelineComparisonRenderer.decodeFrames(result, atMs)
                try {
                    assertEquals(pair.before.width, pair.after.width)
                    assertEquals(pair.before.height, pair.after.height)
                    assertFalse("Grade must change the actual encoded composition at $atMs ms", pair.before.sameAs(pair.after))
                } finally {
                    pair.before.recycle()
                    pair.after.recycle()
                }
            }
            assertArrayEquals("Comparison reads must not re-encode the shareable After file", afterHash, sha256(result.after))
            assertArrayEquals("Original source must remain unchanged", originalHash, sha256(source))
        } finally {
            comparison?.before?.delete()
            comparison?.after?.delete()
            source.delete()
        }
    }

    private fun sha256(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
}
