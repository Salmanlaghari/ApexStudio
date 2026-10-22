package com.apexstudio.app.ui.screens.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.apexstudio.app.MainActivity
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.ApexTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Installed Android UI; all state is test-only and has no project repository. */
@RunWith(AndroidJUnit4::class)
class LandscapeEditorInstrumentedTest {
    @Test fun landscapeEditorDecodesVideoAndEditsRealFrameKeyframesWithoutLosingLayers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val source = File(context.cacheDir, "landscape-test-only.mp4")
        instrumentation.context.assets.open("premium_editor/twelve_second_fixture.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val clip = MediaClip("landscape-main", "Landscape test footage", Uri.fromFile(source).toString(), 12_000L,
            trimEndMs = 12_000L, textOverlays = listOf(TextOverlay(id = "title", text = "ApexStudio", startMs = 0L, endMs = 8000L)))
        val audio = AudioTrack("music", "Fixture audio", clip.uri, trimEndMs = 12_000L, sourceDurationMs = 12_000L)
        val project = Project("landscape-ui-test-only", "Landscape editor test", 12_000L, clips = listOf(clip), audioTracks = listOf(audio), fps = 30)
        lateinit var vm: EditorViewModel
        device.setOrientationLeft()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    vm = EditorViewModel()
                    vm._state.value = vm.state.value.copy(project = project, selectedClipId = clip.id,
                        durationMs = 12_000L, timelineZoom = .5f)
                    activity.setContent {
                        ApexTheme { EditorScreen(project.id, {}, {}, {}, {}, vm = vm) }
                    }
                }
                // A cold software-only emulator can have a focused window but a null
                // accessibility root while Compose/JIT starts. Wait for the actual
                // editor first; keep the independent panel/action assertions intact.
                val editorReady = device.wait(Until.hasObject(By.desc("ApexStudio build identity. Project ${project.id}")), 180_000)
                val panelAvailable = editorReady && device.wait(Until.hasObject(By.text("Hide properties ›")), 60_000)
                val diagnostics = File(context.getExternalFilesDir(null), "landscape-runtime-diagnostics").apply { mkdirs() }
                device.takeScreenshot(File(diagnostics, "panel-check.png"))
                device.dumpWindowHierarchy(File(diagnostics, "panel-check.xml"))
                scenario.onActivity { activity ->
                    File(diagnostics, "panel-check.txt").writeText(
                        "editorReady=$editorReady panelAvailable=$panelAvailable\n" +
                            "display=${device.displayWidth}x${device.displayHeight}\n" +
                            "configuration=${activity.resources.configuration}\n" +
                            "root=${activity.window.decorView.width}x${activity.window.decorView.height}\n" +
                            "project=${vm.state.value.project?.id} selected=${vm.state.value.selectedClipId}\n" +
                            "position=${vm.livePlayheadMs()} ready=${vm.state.value.isPlayerReady} error=${vm.state.value.playerError}\n"
                    )
                }
                assertTrue("Editor accessibility root unavailable during startup (see landscape-runtime-diagnostics)", editorReady)
                assertTrue("Landscape properties panel unavailable (see landscape-runtime-diagnostics)", panelAvailable)
                assertTrue("Must actually be landscape", device.displayWidth > device.displayHeight)
                assertTrue("Frame-step control unavailable", device.wait(Until.hasObject(By.text("+1f")), 15_000))
                assertTrue("Horizontal editing dock unavailable", device.wait(Until.hasObject(By.desc("More")), 15_000))
                device.findObject(By.text("Add / remove keyframe")).click()
                await { vm.state.value.project!!.clips.first().keyframes.keyframes.size == 1 }
                device.findObject(By.text("+1f")).click()
                await { vm.livePlayheadMs() == 33L }
                device.findObject(By.text("Add / remove keyframe")).click()
                await { vm.state.value.project!!.clips.first().keyframes.keyframes.size == 2 }
                assertEquals(listOf(0L, 33L), vm.state.value.project!!.clips.first().keyframes.keyframes.map { it.timeMs }.sorted())
                assertEquals(project.audioTracks, vm.state.value.project!!.audioTracks)
                assertEquals(clip.textOverlays, vm.state.value.project!!.clips.first().textOverlays)
                device.findObject(By.text("Hide properties ›")).click()
                assertTrue(device.wait(Until.hasObject(By.text("‹")), 10_000))
                device.findObject(By.text("‹")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Hide properties ›")), 10_000))
                await(60_000L) { vm.state.value.isPlayerReady || vm.state.value.playerError != null }
                assertTrue("Real decoder not ready: ${vm.state.value.playerError}", vm.state.value.isPlayerReady)
                assertFalse(device.hasObject(By.text("Lenses")))
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "landscape-editor-installed.png")))
            }
        } finally {
            device.unfreezeRotation()
            source.delete()
        }
    }

    private fun await(timeout: Long = 10_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100L)
        assertTrue("Timed out waiting for editor state", condition())
    }
}
