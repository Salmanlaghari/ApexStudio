package com.apexstudio.app.ui.screens.editor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.apexstudio.app.MainActivity
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.setPlaying
import com.apexstudio.app.ui.theme.ApexTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real editor/codec acceptance; all media and exports are test-only cache/external-files artifacts. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class RuntimeEditAcceptanceInstrumentedTest {
    @Test
    fun playbackSurvivesLiveEditsPausedPreviewChangesAndProcessedMp4Decodes(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        Configurator.getInstance().setWaitForIdleTimeout(500L)
        device.wakeUp()
        device.setOrientationNatural()
        val diagnostics = File(context.getExternalFilesDir(null), "runtime-edit-diagnostics").apply { mkdirs() }
        val source = File(context.cacheDir, "runtime-edit-acceptance-${UUID.randomUUID()}.mp4")
        val screenshotA = File(context.cacheDir, "runtime-preview-a-${UUID.randomUUID()}.png")
        val screenshotB = File(context.cacheDir, "runtime-preview-b-${UUID.randomUUID()}.png")
        val exportName = "runtime-edit-acceptance-${UUID.randomUUID()}.mp4"
        val exportDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        var exported: File? = null
        var previewA: Bitmap? = null
        var previewB: Bitmap? = null
        val engine = ExportEngine(context)
        try {
            instrumentation.context.assets.open("premium_editor/runtime_color_chart.mp4").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            FilterManifest.initialize(context)
            val filter = requireNotNull(FilterManifest.presetById("noir_classic")) {
                "Bundled noir_classic LUT must be available to both preview and export"
            }
            val fx = requireNotNull(FxPreset.byId("vignette"))
            val sourceUri = Uri.fromFile(source).toString()
            val startingMask = VideoMask(
                shape = MaskShape.CIRCLE,
                centerX = .5f,
                centerY = .5f,
                sizeX = .58f,
                sizeY = .58f,
                feather = .02f
            )
            val editedMask = startingMask.copy(centerX = .34f, centerY = .53f, sizeX = .48f)
            val clip = MediaClip(
                id = "runtime-acceptance-main",
                name = "Runtime acceptance fixture",
                uri = sourceUri,
                durationMs = 12_000L,
                trimEndMs = 12_000L
            )
            val project = Project(
                id = "runtime-acceptance-${UUID.randomUUID()}",
                name = "Runtime acceptance",
                durationMs = 12_000L,
                clips = listOf(clip),
                fps = 10
            )
            lateinit var vm: EditorViewModel
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    // A context-free VM cannot save or overwrite any user project.
                    vm = EditorViewModel()
                    vm._state.value = vm.state.value.copy(
                        project = project,
                        selectedClipId = clip.id,
                        durationMs = project.durationMs,
                        playerPositionMs = 0L,
                        isPlaying = false
                    )
                    activity.setContent {
                        ApexTheme {
                            EditorScreen(project.id, onBack = {}, onExport = {}, onColor = {}, onAudio = {}, vm = vm)
                        }
                    }
                }
                assertTrue(
                    "Installed editor accessibility root did not appear",
                    device.wait(Until.hasObject(By.desc("ApexStudio build identity. Project ${project.id}")), 180_000L)
                )
                val readyDeadline = System.currentTimeMillis() + 90_000L
                while (!vm.state.value.isPlayerReady && vm.state.value.playerError == null &&
                    System.currentTimeMillis() < readyDeadline
                ) Thread.sleep(100L)
                assertTrue("Fixture video did not become ready: ${vm.state.value.playerError}", vm.state.value.isPlayerReady)

                // Start playback, then apply the same filter/FX/mask inputs exercised by export.
                vm.setPlaying(true)
                val initialPosition = awaitPositionAfter(vm, 0L, 15_000L)
                instrumentation.runOnMainSync {
                    val current = vm.state.value
                    val currentProject = requireNotNull(current.project)
                    vm._state.value = current.copy(
                        activeFilterId = filter.id,
                        filterIntensity = .85f,
                        activeFxId = fx.id,
                        fxIntensity = .8f,
                        project = currentProject.copy(clips = listOf(clip.copy(mask = startingMask)))
                    )
                }
                val editedPosition = awaitPositionAfter(vm, initialPosition + 200L, 10_000L)
                assertTrue("Playback must remain enabled while preview edits are applied", vm.state.value.isPlaying)
                assertTrue("Player position must advance across live edits", editedPosition > initialPosition)

                vm.setPlaying(false)
                val pauseDeadline = System.currentTimeMillis() + 5_000L
                while (vm.state.value.isPlaying && System.currentTimeMillis() < pauseDeadline) Thread.sleep(50L)
                assertTrue("Editor did not reach paused state", !vm.state.value.isPlaying)
                Thread.sleep(350L)
                // Isolate each grade: a moving mask alone must not make a broken LUT/FX pass.
                instrumentation.runOnMainSync {
                    val current = vm.state.value
                    vm._state.value = current.copy(filterIntensity = 0f, fxIntensity = 0f,
                        project = current.project!!.copy(clips = listOf(clip)))
                }
                val baseline = awaitPreview(device, context.packageName, File(diagnostics, "ungraded.png")) {
                    saturatedPixelCount(it) > 300
                }
                instrumentation.runOnMainSync { vm._state.value = vm.state.value.copy(filterIntensity = .85f) }
                val filtered = awaitPreview(device, context.packageName, File(diagnostics, "filter-only.png")) {
                    changedPixelCount(baseline, it) > 50
                }
                assertTrue("Paused LUT must change actual preview pixels", changedPixelCount(baseline, filtered) > 50)
                instrumentation.runOnMainSync { vm._state.value = vm.state.value.copy(fxIntensity = .8f) }
                val withFx = awaitPreview(device, context.packageName, File(diagnostics, "filter-and-fx.png")) {
                    changedPixelCount(filtered, it) > 50
                }
                assertTrue("Paused FX must change actual preview pixels", changedPixelCount(filtered, withFx) > 50)
                baseline.recycle()
                filtered.recycle()
                withFx.recycle()
                instrumentation.runOnMainSync {
                    val current = vm.state.value
                    vm._state.value = current.copy(project = current.project!!.copy(clips = listOf(clip.copy(mask = startingMask))))
                }
                Thread.sleep(1_000L)
                previewA = capturePreview(device, context.packageName, screenshotA)

                // Change the visible mask geometry and intensities with transport paused.
                instrumentation.runOnMainSync {
                    val current = vm.state.value
                    val currentProject = requireNotNull(current.project)
                    vm._state.value = current.copy(
                        filterIntensity = .35f,
                        fxIntensity = .55f,
                        project = currentProject.copy(clips = listOf(clip.copy(mask = editedMask)))
                    )
                }
                device.waitForIdle(750L)
                Thread.sleep(350L)
                previewB = capturePreview(device, context.packageName, screenshotB)
                val changed = changedPixelCount(previewA!!, previewB!!)
                assertTrue(
                    "Paused preview must visibly update after filter/FX/mask changes; changed pixels=$changed",
                    changed > 150
                )
                screenshotA.copyTo(File(diagnostics, "paused-before.png"), overwrite = true)
                screenshotB.copyTo(File(diagnostics, "paused-after.png"), overwrite = true)
                File(diagnostics, "preview.txt").writeText("initialPosition=$initialPosition\neditedPosition=$editedPosition\nchangedPixels=$changed\n")
                assertTrue("Paused edits must not resume playback", !vm.state.value.isPlaying)
                assertTrue("The edited mask must remain on the test clip", vm.state.value.project!!.clips.single().mask == editedMask)

                // Keep the actual bottom panel open while dragging the preview mask body.
                instrumentation.runOnMainSync { vm._state.value = vm.state.value.copy(maskPanelOpen = true) }
                Thread.sleep(1_000L)
                val bounds = requireNotNull(device.findObject(By.desc("Video preview frame"))).visibleBounds
                val x = bounds.left + (bounds.width() * editedMask.centerX).toInt()
                val y = bounds.top + (bounds.height() * editedMask.centerY).toInt()
                device.swipe(x, y, x + bounds.width() / 8, y, 20)
                Thread.sleep(750L)
                val dragged = vm.state.value.project!!.clips.single().mask!!
                assertTrue("Open mask panel must not intercept preview body drag", dragged.centerX > editedMask.centerX + .02f)
                device.takeScreenshot(File(diagnostics, "mask-panel-drag.png"))
                File(diagnostics, "mask-drag.txt").writeText("beforeCenterX=${editedMask.centerX}\nafterCenterX=${dragged.centerX}\n")
            }

            // Run the production engine on a short trim at its smallest supported export size.
            engine.startExport(
                sourceUri,
                ExportEngine.ExportConfig(
                    resolution = "720p",
                    fps = 10,
                    aspectRatio = "16:9",
                    trimEndMs = 1_000L,
                    filterPreset = filter,
                    filterIntensity = .35f,
                    fxPreset = fx,
                    fxIntensity = .55f,
                    mask = editedMask
                ),
                exportName
            )
            val result = withTimeout(120_000L) {
                engine.exportState.first { it.error != null || it.outputUri != null }
            }
            assertNull("Processed MP4 export must complete: ${result.error}", result.error)
            val outputPath = requireNotNull(Uri.parse(result.outputUri).path) { "Export returned no local output path" }
            exported = File(outputPath)
            assertTrue("Processed MP4 must exist and be non-empty: $outputPath", exported!!.isFile && exported!!.length() > 0L)

            val retriever = android.media.MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(exported!!.absolutePath)
                retriever.getFrameAtTime(200_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release()
            }
            assertNotNull("Exported MP4 must contain a decodable processed video frame", frame)
            frame!!.useBitmap { bitmap ->
                assertTrue("Decoded export frame has invalid dimensions", bitmap.width > 0 && bitmap.height > 0)
                val center = averageLuma(bitmap, .40f, .40f, .60f, .60f)
                val outsideMask = averageLuma(bitmap, .06f, .06f, .14f, .14f)
                assertTrue(
                    "Exported mask should keep its inner region and darken the outside (center=$center outside=$outsideMask)",
                    center > outsideMask + 8.0
                )
                File(diagnostics, "export.txt").writeText("bytes=${exported!!.length()}\nwidth=${bitmap.width}\nheight=${bitmap.height}\ncenterLuma=$center\noutsideLuma=$outsideMask\n")
            }
            exported!!.copyTo(File(diagnostics, "processed-export.mp4"), overwrite = true)
        } finally {
            engine.release()
            source.delete()
            screenshotA.delete()
            screenshotB.delete()
            exported?.delete()
            File(exportDir, exportName).delete()
            File(exportDir, exportName.removeSuffix(".mp4") + "_music.mp4").delete()
            previewA?.recycle()
            previewB?.recycle()
            device.unfreezeRotation()
        }
    }

    private fun awaitPositionAfter(vm: EditorViewModel, afterMs: Long, timeoutMs: Long): Long {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = vm.state.value
            if (state.playerPositionMs > afterMs) return state.playerPositionMs
            Thread.sleep(50L)
        }
        throw AssertionError("Preview transport did not advance past $afterMs ms; last=${vm.state.value.playerPositionMs}, error=${vm.state.value.playerError}")
    }

    private fun awaitPreview(device: UiDevice, packageName: String, destination: File, predicate: (Bitmap) -> Boolean): Bitmap {
        val deadline = System.currentTimeMillis() + 60_000L
        do {
            val frame = capturePreview(device, packageName, destination)
            if (predicate(frame)) return frame
            frame.recycle()
            Thread.sleep(1_000L)
        } while (System.currentTimeMillis() < deadline)
        throw AssertionError("Expected decoded preview pixels did not arrive: ${destination.name}")
    }

    private fun saturatedPixelCount(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
            val p = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(p)
            val g = android.graphics.Color.green(p)
            val b = android.graphics.Color.blue(p)
            if (maxOf(r, g, b) - minOf(r, g, b) > 80) count++
        }
        return count
    }

    private fun capturePreview(device: UiDevice, packageName: String, destination: File): Bitmap {
        device.waitForIdle(500L)
        assertTrue("Could not capture editor preview screenshot", device.takeScreenshot(destination))
        val whole = BitmapFactory.decodeFile(destination.absolutePath)
            ?: throw AssertionError("Screenshot could not be decoded: ${destination.absolutePath}")
        val playerBounds = requireNotNull(device.findObject(By.desc("Video preview frame"))) {
            "Actual video preview bounds must be available; UI pixels cannot substitute for video pixels"
        }.visibleBounds
        val crop = playerBounds.let { bounds ->
            Rect(
                bounds.left.coerceIn(0, whole.width - 1),
                bounds.top.coerceIn(0, whole.height - 1),
                bounds.right.coerceIn(1, whole.width),
                bounds.bottom.coerceIn(1, whole.height)
            )
        }
        if (crop.width() < 2 || crop.height() < 2) {
            whole.recycle()
            throw AssertionError("Preview screenshot crop is empty: $crop")
        }
        return Bitmap.createBitmap(whole, crop.left, crop.top, crop.width(), crop.height()).also { whole.recycle() }
    }

    private fun changedPixelCount(first: Bitmap, second: Bitmap): Int {
        if (first.width != second.width || first.height != second.height) return first.width * first.height
        var changed = 0
        for (y in 0 until first.height step 2) {
            for (x in 0 until first.width step 2) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)
                val delta = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
                if (delta > 36) changed++
            }
        }
        return changed
    }

    private fun averageLuma(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Double {
        val x0 = (bitmap.width * left).toInt().coerceIn(0, bitmap.width - 1)
        val y0 = (bitmap.height * top).toInt().coerceIn(0, bitmap.height - 1)
        val x1 = (bitmap.width * right).toInt().coerceIn(x0 + 1, bitmap.width)
        val y1 = (bitmap.height * bottom).toInt().coerceIn(y0 + 1, bitmap.height)
        var total = 0.0
        var count = 0
        for (y in y0 until y1 step 4) for (x in x0 until x1 step 4) {
            val color = bitmap.getPixel(x, y)
            total += .2126 * android.graphics.Color.red(color) +
                .7152 * android.graphics.Color.green(color) +
                .0722 * android.graphics.Color.blue(color)
            count++
        }
        return if (count == 0) 0.0 else total / count
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
        try { block(this) } finally { recycle() }
    }
}
