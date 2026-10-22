package com.apexstudio.app.ui.screens.editor

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.apexstudio.app.MainActivity
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.VideoFitMode
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.ui.theme.ApexTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the installed editor preview against decoded sources of three natural aspect ratios. */
@RunWith(AndroidJUnit4::class)
class PreviewFitCoverInstrumentedTest {
    private data class Fixture(val name: String, val width: Int, val height: Int)

    @Test
    fun decodedPortraitLandscapeAndSquareSourcesCanSwitchPreviewFitAndCover() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val diagnostics = File(context.getExternalFilesDir(null) ?: context.filesDir, "preview-fit-cover-diagnostics")
            .apply { mkdirs() }
        val fixtures = listOf(
            Fixture("portrait", 240, 426),
            Fixture("landscape", 426, 240),
            Fixture("square", 320, 320)
        )

        device.setOrientationNatural()
        try {
            fixtures.forEach { fixture ->
                val source = File(context.cacheDir, "preview-fit-cover-${fixture.name}.mp4")
                instrumentation.context.assets.open("preview_fit_cover/${fixture.name}.mp4").use { input ->
                    source.outputStream().use { output -> input.copyTo(output) }
                }
                try {
                    val uri = Uri.fromFile(source).toString()
                    val durationMs = 1_500L
                    val clip = MediaClip(
                        id = "preview-fit-cover-${fixture.name}",
                        name = "${fixture.name} test source",
                        uri = uri,
                        durationMs = durationMs,
                        trimEndMs = durationMs,
                        videoFitMode = VideoFitMode.COVER
                    )
                    val project = Project(
                        id = "preview-fit-cover-${fixture.name}-project",
                        name = "${fixture.name.replaceFirstChar(Char::uppercase)} preview test",
                        durationMs = durationMs,
                        clips = listOf(clip),
                        fps = 8
                    )
                    val vm = EditorViewModel()
                    vm._state.value = vm.state.value.copy(
                        project = project,
                        selectedClipId = clip.id,
                        durationMs = durationMs
                    )

                    // Check the video stream itself, independently of Compose's viewport geometry.
                    val retriever = MediaMetadataRetriever()
                    val sourceSize = try {
                        retriever.setDataSource(source.absolutePath)
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            ?.toIntOrNull()
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ?.toIntOrNull()
                        width to height
                    } finally {
                        retriever.release()
                    }
                    assertEquals("Unexpected decoded fixture width for ${fixture.name}", fixture.width, sourceSize.first)
                    assertEquals("Unexpected decoded fixture height for ${fixture.name}", fixture.height, sourceSize.second)

                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                        scenario.onActivity { activity ->
                            activity.setContent {
                                ApexTheme {
                                    EditorScreen(project.id, onBack = {}, onExport = {}, onColor = {}, onAudio = {}, vm = vm)
                                }
                            }
                        }

                        val root = device.wait(
                            Until.hasObject(By.desc("ApexStudio build identity. Project ${project.id}")),
                            180_000L
                        )
                        assertTrue("Editor accessibility root did not appear for ${fixture.name}", root)

                        val decodeDeadline = System.currentTimeMillis() + 60_000L
                        while (!vm.state.value.isPlayerReady && vm.state.value.playerError == null &&
                            System.currentTimeMillis() < decodeDeadline
                        ) {
                            Thread.sleep(200L)
                        }
                        assertTrue(
                            "${fixture.name} video did not reach decoded player-ready state: ${vm.state.value.playerError}",
                            vm.state.value.isPlayerReady
                        )

                        val fitLabel = "Preview mode: Fit"
                        val coverLabel = "Preview mode: Cover"
                        val initialMode = when {
                            device.hasObject(By.desc(fitLabel)) -> "Fit"
                            device.hasObject(By.desc(coverLabel)) -> "Cover"
                            else -> null
                        }
                        assertNotNull("Fit/Cover selector missing for ${fixture.name}", initialMode)
                        assertEquals("Imported-video framing must start Cover", "Cover", initialMode)
                        val initialLabel = if (initialMode == "Fit") fitLabel else coverLabel
                        val alternateLabel = if (initialMode == "Fit") coverLabel else fitLabel
                        capture(device, diagnostics, fixture.name, initialMode!!)

                        // Drive the accessible top-bar control, then verify its visible state flips.
                        device.findObject(By.desc(initialLabel)).click()
                        assertTrue(
                            "Top-bar selector did not switch ${fixture.name} preview from $initialMode",
                            device.wait(Until.hasObject(By.desc(alternateLabel)), 10_000L)
                        )
                        assertEquals("Fit toggle must persist on the real clip", VideoFitMode.FIT,
                            vm.state.value.project!!.clips.single().videoFitMode)
                        capture(device, diagnostics, fixture.name, if (initialMode == "Fit") "Cover" else "Fit")

                        // Toggling presentation mode must not rewrite the clip's source or time geometry.
                        assertEquals(VideoFitMode.COVER, vm.state.value.project!!.clips.single().videoFitMode)
                        val afterToggle = vm.state.value.project!!.clips.single()
                        assertEquals("Preview toggle rewrote ${fixture.name} source URI", uri, afterToggle.uri)
                        assertEquals("Preview toggle rewrote ${fixture.name} source duration", durationMs, afterToggle.durationMs)
                        assertEquals("Preview toggle rewrote ${fixture.name} trim start", clip.trimStartMs, afterToggle.trimStartMs)
                        assertEquals("Preview toggle rewrote ${fixture.name} trim end", clip.trimEndMs, afterToggle.trimEndMs)
                    }
                } finally {
                    source.delete()
                }
            }
        } finally {
            device.unfreezeRotation()
        }
    }

    private fun capture(device: UiDevice, diagnostics: File, source: String, mode: String) {
        device.waitForIdle(500L)
        val screenshot = File(diagnostics, "${source}-${mode.lowercase()}.png")
        assertTrue("Could not save ${source} $mode preview screenshot at ${screenshot.absolutePath}",
            device.takeScreenshot(screenshot))
    }
}
