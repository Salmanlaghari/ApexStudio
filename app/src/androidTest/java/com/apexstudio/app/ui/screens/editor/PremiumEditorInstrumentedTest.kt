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
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.ui.theme.ApexTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real installed-app UI with explicitly test-only media, never the owner's project. */
@RunWith(AndroidJUnit4::class)
class PremiumEditorInstrumentedTest {
    @Test fun premiumEditorKeepsPreviewTransportPropertiesAndDockOnScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "premium-editor-test-only.mp4")
        instrumentation.context.assets.open("premium_editor/twelve_second_fixture.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val uri = Uri.fromFile(source).toString()
        val title = TextOverlay(id = "premium-title", text = "Beyond the ordinary",
            x = .3f, y = .7f, startMs = 0L, endMs = 8000L)
        val clip = MediaClip("premium-main", "UI test footage", uri, 12000L,
            trimEndMs = 12000L, textOverlays = listOf(title),
            workspaceEffects = listOf(WorkspaceEffect(WorkspaceEffectPresets.WARM, 0L, 12000L)))
        val overlay = clip.copy(id = "premium-overlay", name = "Overlay test", type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = 6000L, trimEndMs = 3000L,
            textOverlays = emptyList(), workspaceEffects = emptyList())
        val audio = AudioTrack("premium-music", "Test music", uri, trimEndMs = 12000L,
            sourceDurationMs = 12000L, kind = AudioTrack.Kind.MUSIC)
        val project = Project("premium-ui-test-only", "Premium UI test", 12000L,
            clips = listOf(clip, overlay), audioTracks = listOf(audio, audio.copy(id = "premium-voice",
                name = "Test audio", kind = AudioTrack.Kind.SFX)),
            stickers = listOf(StickerOverlay(id = "premium-sticker", symbolOrUri = "★", name = "Test star",
                x = .8f, y = .3f, startMs = 3000L, endMs = 9000L)))
        lateinit var vm: EditorViewModel
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                // No repository/context means this fixture cannot save over user project records.
                vm = EditorViewModel()
                vm._state.value = vm.state.value.copy(project = project, selectedClipId = clip.id,
                    durationMs = 12000L, timelineZoom = .5f, activeFxId = "bloom", fxIntensity = .25f)
                activity.setContent {
                    ApexTheme {
                        EditorScreen(project.id, onBack = {}, onExport = {}, onColor = {}, onAudio = {}, vm = vm)
                    }
                }
            }
            assertTrue("Editor did not appear", device.wait(Until.hasObject(By.desc("Add keyframe")), 30000))
            assertTrue(device.wait(Until.hasObject(By.text("Position")), 10000))
            val previewDeadline = System.currentTimeMillis() + 30000L
            while (!vm.state.value.isPlayerReady && System.currentTimeMillis() < previewDeadline) Thread.sleep(200)
            device.waitForIdle(1000)
            val screenshot = File(context.getExternalFilesDir(null), "premium-editor-installed.png")
            assertTrue("Installed-app screenshot failed", device.takeScreenshot(screenshot))
            assertTrue("Decoded preview must be ready, not a drawn placeholder: ${vm.state.value.playerError}", vm.state.value.isPlayerReady)
            val add = device.findObject(By.desc("Add keyframe"))
            val dock = device.findObject(By.desc("More"))
            assertNotNull("Bottom horizontal dock must remain visible", dock)
            assertTrue("Dock must stay in safe screen bounds", dock.visibleBounds.bottom <= device.displayHeight)
            assertTrue("Controls must sit above dock", add.visibleBounds.bottom < dock.visibleBounds.top)
            add.click()
            val deadline = System.currentTimeMillis() + 5000L
            while (vm.state.value.project!!.clips.first().keyframes.keyframes.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertEquals(1, vm.state.value.project!!.clips.first().keyframes.keyframes.size)
            assertEquals(uri, vm.state.value.project!!.clips.first().uri)
            assertEquals(project.audioTracks, vm.state.value.project!!.audioTracks)
            assertEquals(project.stickers, vm.state.value.project!!.stickers)
            assertFalse("Camera/Lenses must not return to editor", device.hasObject(By.text("Lenses")))
        }
        source.delete()
    }
}
