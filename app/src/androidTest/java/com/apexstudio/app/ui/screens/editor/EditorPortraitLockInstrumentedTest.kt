package com.apexstudio.app.ui.screens.editor

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.apexstudio.app.MainActivity
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.seekTo
import com.apexstudio.app.ui.theme.ApexTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production EditorScreen orientation effect and real editor actions on a phone-sized emulator. */
@RunWith(AndroidJUnit4::class)
class EditorPortraitLockInstrumentedTest {
    @Test fun editorLocksPortraitAndRestoresPreviousOrientationAfterExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val source = File(context.cacheDir, "portrait-lock-test-only.mp4")
        instrumentation.context.assets.open("premium_editor/twelve_second_fixture.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val clip = MediaClip(
            "portrait-main",
            "Portrait test footage",
            Uri.fromFile(source).toString(),
            12_000L,
            trimEndMs = 12_000L,
            textOverlays = listOf(TextOverlay(id = "title", text = "ApexStudio", startMs = 0L, endMs = 8_000L)),
        )
        val audio = AudioTrack("music", "Fixture audio", clip.uri, trimEndMs = 12_000L, sourceDurationMs = 12_000L)
        val project = Project("portrait-lock-test-only", "Portrait editor test", 12_000L,
            clips = listOf(clip), audioTracks = listOf(audio), fps = 30)
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = project,
                selectedClipId = clip.id,
                durationMs = 12_000L,
                timelineZoom = .5f,
            )
        }

        var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        device.setOrientationLeft()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    previousOrientation = activity.requestedOrientation
                    activity.setContent {
                        ApexTheme {
                            var editorVisible by remember { mutableStateOf(true) }
                            if (editorVisible) {
                                EditorScreen(
                                    projectId = project.id,
                                    onBack = { editorVisible = false },
                                    onExport = {},
                                    onColor = {},
                                    onAudio = {},
                                    vm = vm,
                                )
                            } else {
                                Text("Exited editor")
                            }
                        }
                    }
                }

                val editorReady = device.wait(
                    Until.hasObject(By.desc("ApexStudio build identity. Project ${project.id}")),
                    180_000,
                )
                assertTrue("Editor accessibility content did not load", editorReady)
                await("editor did not switch to portrait") { device.displayHeight > device.displayWidth }
                scenario.onActivity { activity ->
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
                    assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
                }
                assertFalse("Landscape properties panel must not be active in the portrait-locked editor",
                    device.hasObject(By.desc("Hide video properties")))

                assertTrue("Portrait keyframe action unavailable",
                    device.wait(Until.hasObject(By.desc("Add keyframe")), 15_000))
                device.findObject(By.desc("Add keyframe")).click()
                await("first keyframe was not recorded") {
                    vm.state.value.project!!.clips.first().keyframes.keyframes.size == 1
                }
                scenario.onActivity { vm.seekTo(33L) }
                await("playhead did not advance by one frame") { vm.livePlayheadMs() == 33L }
                assertTrue("Second portrait keyframe action unavailable",
                    device.wait(Until.hasObject(By.desc("Add keyframe")), 15_000))
                device.findObject(By.desc("Add keyframe")).click()
                await("second keyframe was not recorded") {
                    vm.state.value.project!!.clips.first().keyframes.keyframes.size == 2
                }
                assertEquals(listOf(0L, 33L), vm.state.value.project!!.clips.first().keyframes.keyframes.map { it.timeMs }.sorted())
                assertEquals(project.audioTracks, vm.state.value.project!!.audioTracks)
                assertEquals(clip.textOverlays, vm.state.value.project!!.clips.first().textOverlays)

                await(60_000L, "video player did not become ready") {
                    vm.state.value.isPlayerReady || vm.state.value.playerError != null
                }
                assertTrue("Real decoder not ready: ${vm.state.value.playerError}", vm.state.value.isPlayerReady)
                await(60_000L, "decoded preview frame was not displayed") {
                    vm.previewFrameGeneration.value > 0L
                }

                device.findObject(By.desc("Back to projects")).click()
                assertTrue("Editor did not exit", device.wait(Until.hasObject(By.text("Exited editor")), 15_000))
                var restoredOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                scenario.onActivity { activity -> restoredOrientation = activity.requestedOrientation }
                assertEquals("Leaving the editor must restore the Activity's prior request",
                    previousOrientation, restoredOrientation)
                await(15_000L, "device orientation was not released after leaving the editor") {
                    device.displayWidth > device.displayHeight
                }
            }
        } finally {
            device.unfreezeRotation()
            source.delete()
        }
    }

    private fun await(message: String, condition: () -> Boolean) = await(15_000L, message, condition)

    private fun await(timeout: Long, message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100L)
        assertTrue(message, condition())
    }
}
