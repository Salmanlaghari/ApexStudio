package com.apexstudio.app.data.export

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Independent native export acceptance: UI failures cannot hide encoder/shader failures. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class MaskedLookExportInstrumentedTest {
    @Test
    fun lutFxAndMaskExportProducesDecodableMaskedFrames(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "masked-look-${UUID.randomUUID()}.mp4")
        val diagnostics = File(context.getExternalFilesDir(null), "masked-look-export-diagnostics").apply { mkdirs() }
        val engine = ExportEngine(context)
        var output: File? = null
        try {
            instrumentation.context.assets.open("premium_editor/runtime_color_chart.mp4").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            FilterManifest.initialize(context)
            engine.startExport(
                Uri.fromFile(source).toString(),
                ExportEngine.ExportConfig(
                    resolution = "720p", fps = 10, aspectRatio = "16:9", trimEndMs = 1_000L,
                    filterPreset = requireNotNull(FilterManifest.presetById("noir_classic")), filterIntensity = .85f,
                    fxPreset = requireNotNull(FxPreset.byId("vignette")), fxIntensity = .8f,
                    mask = VideoMask(shape = MaskShape.RECTANGLE, centerX = .5f, centerY = .5f, sizeX = .5f, sizeY = .5f)
                ),
                "masked-look-${UUID.randomUUID()}.mp4"
            )
            val result = withTimeout(180_000L) {
                engine.exportState.first { it.error != null || it.outputUri != null }
            }
            assertNull("Native masked LUT/FX export must complete: ${result.error}", result.error)
            output = File(requireNotNull(Uri.parse(result.outputUri).path))
            assertTrue("Exported MP4 must be non-empty", output!!.length() > 0L)
            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(output!!.absolutePath)
                requireNotNull(retriever.getFrameAtTime(200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
            } finally { retriever.release() }
            try {
                fun luma(x: Float, y: Float): Double {
                    val p = frame.getPixel((frame.width * x).toInt(), (frame.height * y).toInt())
                    return .2126 * android.graphics.Color.red(p) + .7152 * android.graphics.Color.green(p) + .0722 * android.graphics.Color.blue(p)
                }
                val inside = luma(.5f, .5f)
                val outside = luma(.05f, .05f)
                assertTrue("Mask must keep the center and remove outside pixels ($inside vs $outside)", inside > outside + 8.0)
                File(diagnostics, "result.txt").writeText("bytes=${output!!.length()}\nwidth=${frame.width}\nheight=${frame.height}\ninsideLuma=$inside\noutsideLuma=$outside\n")
                File(diagnostics, "decoded-frame.png").outputStream().use { frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                output!!.copyTo(File(diagnostics, "masked-look.mp4"), overwrite = true)
            } finally { frame.recycle() }
        } finally {
            instrumentation.runOnMainSync { engine.release() }
            source.delete()
            output?.delete()
        }
    }
}
