package com.apexstudio.app.ui.screens.editor

import android.content.Intent
import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.apexstudio.app.MainActivity
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.ui.theme.ApexTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Installed-app portrait regression test for the restored stacked editor layout. */
@RunWith(AndroidJUnit4::class)
class RestoredEditorInstrumentedTest {
    @Test
    fun restoredPortraitEditorDecodesVideoKeepsStackedWorkspaceAndEditsKeyframe() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        Configurator.getInstance().setWaitForIdleTimeout(500L)
        val source = File(context.cacheDir, "restored-editor-portrait-test-only.mp4")
        instrumentation.context.assets.open("premium_editor/twelve_second_fixture.mp4").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = Uri.fromFile(source).toString()
        val title = TextOverlay(
            id = "restored-title",
            text = "Beyond the ordinary",
            x = .3f,
            y = .7f,
            startMs = 0L,
            endMs = 8_000L
        )
        val clip = MediaClip(
            id = "restored-portrait-main",
            name = "Restored portrait footage",
            uri = uri,
            durationMs = 12_000L,
            trimEndMs = 12_000L,
            textOverlays = listOf(title)
        )
        val overlay = clip.copy(
            id = "restored-portrait-overlay",
            name = "Overlay test",
            type = ClipType.OVERLAY,
            trackIndex = 1,
            timelineOffsetMs = 6_000L,
            trimEndMs = 3_000L,
            textOverlays = emptyList()
        )
        val audio = AudioTrack(
            id = "restored-portrait-music",
            name = "Test music",
            uri = uri,
            trimEndMs = 12_000L,
            sourceDurationMs = 12_000L,
            kind = AudioTrack.Kind.MUSIC
        )
        val audioTracks = listOf(audio, audio.copy(id = "restored-portrait-sfx", name = "Test audio",
            kind = AudioTrack.Kind.SFX))
        val stickers = listOf(StickerOverlay(
            id = "restored-portrait-sticker",
            symbolOrUri = "★",
            name = "Test star",
            x = .8f,
            y = .3f,
            startMs = 3_000L,
            endMs = 9_000L
        ))
        val project = Project(
            id = "restored-portrait-ui-test-only",
            name = "Restored portrait UI test",
            durationMs = 12_000L,
            clips = listOf(clip, overlay),
            audioTracks = audioTracks,
            stickers = stickers,
            fps = 30
        )
        val diagnostics = File(context.getExternalFilesDir(null) ?: context.filesDir, "restored-editor-diagnostics")
            .apply { mkdirs() }
        lateinit var vm: EditorViewModel

        // Use natural orientation rather than assuming the emulator's current rotation.
        device.setOrientationNatural()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    // No context/repository is supplied: this fixture exists only in this VM.
                    vm = EditorViewModel()
                    vm._state.value = vm.state.value.copy(
                        project = project,
                        selectedClipId = clip.id,
                        durationMs = 12_000L,
                        timelineZoom = .5f
                    )
                    activity.setContent {
                        ApexTheme {
                            EditorScreen(project.id, onBack = {}, onExport = {}, onColor = {}, onAudio = {}, vm = vm)
                        }
                    }
                }

                val editorReady = device.wait(
                    Until.hasObject(By.desc("ApexStudio build identity. Project ${project.id}")),
                    180_000L
                )
                assertTrue("Editor accessibility root did not appear within 180s", editorReady)

                val decodeDeadline = System.currentTimeMillis() + 60_000L
                while (!vm.state.value.isPlayerReady && vm.state.value.playerError == null &&
                    System.currentTimeMillis() < decodeDeadline
                ) {
                    Thread.sleep(200L)
                }
                // Capture after decoding so the evidence shows the real video, and before assertions
                // that may fail so the screenshot and hierarchy remain available for diagnosis.
                val screenshot = File(diagnostics, "restored-editor-installed.png")
                val hierarchy = File(diagnostics, "restored-editor-installed.xml")
                device.waitForIdle(1_000L)
                device.takeScreenshot(screenshot)
                device.dumpWindowHierarchy(hierarchy)

                assertTrue("Bundled video did not reach decoded player-ready state: ${vm.state.value.playerError}",
                    vm.state.value.isPlayerReady)
                assertTrue("Expected natural portrait orientation", device.displayHeight > device.displayWidth)
                assertTrue("Playback row is missing", device.wait(Until.hasObject(By.desc("Play playback")), 30_000L))
                assertTrue("Portrait timeline header is missing",
                    device.wait(Until.hasObject(By.desc("Expand timeline to full height")), 30_000L))
                assertTrue("Bottom horizontal editing dock is missing", device.wait(Until.hasObject(By.desc("More")), 30_000L))
                assertTrue("Contextual timeline keyframe is missing", device.wait(Until.hasObject(By.desc("Add keyframe")), 10_000L))
                for (property in listOf("Position", "Scale", "Rotation", "Opacity")) {
                    assertTrue("Removed upper property strip must not return: $property",
                        !device.hasObject(By.desc("Open $property keyframe controls")))
                }
                assertTrue("Removed upper animation action must not return",
                    !device.hasObject(By.desc("Edit video keyframe animation")))

                val topBar = device.findObject(By.desc("ApexStudio build identity. Project ${project.id}"))
                val play = device.findObject(By.desc("Play playback"))
                val timeline = device.findObject(By.desc("Expand timeline to full height"))
                val dock = device.findObject(By.desc("More"))
                assertNotNull("Project top bar should remain visible", topBar)
                assertNotNull("Playback control should remain visible", play)
                assertNotNull("Timeline header should remain visible", timeline)
                assertNotNull("Bottom dock should remain visible", dock)
                assertTrue("Playback must remain below the header", topBar.visibleBounds.bottom <= play.visibleBounds.top)
                assertTrue("Timeline must remain below playback", play.visibleBounds.bottom <= timeline.visibleBounds.top)
                assertTrue("Global dock must remain below the timeline", timeline.visibleBounds.bottom <= dock.visibleBounds.top)
                assertTrue("Dock must be reachable within the portrait display", dock.visibleBounds.bottom <= device.displayHeight)

                // The real PlayerView may be wrapped by the platform's accessibility tree.
                val preview = device.findObjects(By.clazz(TextureView::class.java.name))
                    .firstOrNull { it.visibleBounds.width() > 0 && it.visibleBounds.height() > 0 }
                if (preview != null) {
                    assertTrue("Portrait preview should span most of the workspace width: ${preview.visibleBounds}",
                        preview.visibleBounds.width() >= device.displayWidth * 0.72f)
                    assertTrue("Flexible preview should remain stacked above playback: ${preview.visibleBounds} vs ${play.visibleBounds}",
                        preview.visibleBounds.bottom <= play.visibleBounds.top)
                }

                assertTrue("Camera entry must not return to the editor", !device.hasObject(By.text("Camera")))
                assertTrue("Lenses entry must not return to the editor", !device.hasObject(By.text("Lenses")))

                // Exercise the compact contextual header action, not a direct VM toggle.
                device.findObject(By.desc("Add keyframe")).click()
                val keyframeDeadline = System.currentTimeMillis() + 5_000L
                while (vm.state.value.project?.clips?.firstOrNull()?.keyframes?.keyframes.isNullOrEmpty() &&
                    System.currentTimeMillis() < keyframeDeadline
                ) {
                    Thread.sleep(100L)
                }
                val headerKeyframe = device.findObject(By.desc("Remove keyframe"))
                assertNotNull("Header action must switch to remove after creating a pin", headerKeyframe)
                assertTrue("Compact action belongs between timeline focus control and real tracks",
                    headerKeyframe.visibleBounds.top >= timeline.visibleBounds.bottom &&
                        headerKeyframe.visibleBounds.bottom < dock.visibleBounds.top)
                // The lower selected-clip action is independent of the compact header icon.
                assertTrue("Lower Keyframe action is missing", device.wait(Until.hasObject(By.desc("Keyframe")), 10_000L))
                device.findObject(By.desc("Keyframe")).click()
                val removeDeadline = System.currentTimeMillis() + 15_000L
                while (vm.state.value.project!!.clips.first().keyframes.keyframes.isNotEmpty() &&
                    System.currentTimeMillis() < removeDeadline) Thread.sleep(100L)
                assertTrue("Lower Keyframe should remove the same real pin; selected=${vm.state.value.selectedClipId}, pins=${vm.state.value.project!!.clips.first().keyframes.keyframes}, position=${vm.state.value.playerPositionMs}",
                    vm.state.value.project!!.clips.first().keyframes.keyframes.isEmpty())
                device.findObject(By.desc("Keyframe")).click()
                val addDeadline = System.currentTimeMillis() + 15_000L
                while (vm.state.value.project!!.clips.first().keyframes.keyframes.isEmpty() &&
                    System.currentTimeMillis() < addDeadline) Thread.sleep(100L)
                // The selected-clip row is horizontally scrollable on narrow phones.
                val toolsY = device.findObject(By.desc("Keyframe")).visibleBounds.centerY()
                device.swipe(device.displayWidth * 4 / 5, toolsY, device.displayWidth * 2 / 5, toolsY, 25)
                assertTrue("Lower Animation action is missing", device.wait(Until.hasObject(By.desc("Animation")), 10_000L))
                device.findObject(By.desc("Animation")).click()
                val panelDeadline = System.currentTimeMillis() + 15_000L
                while (!vm.state.value.keyframePanelOpen && System.currentTimeMillis() < panelDeadline) Thread.sleep(100L)
                assertTrue("Lower Animation must open its cards panel", vm.state.value.keyframePanelOpen)
                assertTrue("Animation cards need their Close action", device.wait(Until.hasObject(By.desc("Close")), 15_000L))
                val cardY = device.findObject(By.desc("Close")).visibleBounds.bottom +
                    (80 * context.resources.displayMetrics.density).toInt()
                repeat(12) {
                    if (!device.hasObject(By.text("Customized")))
                        device.swipe(device.displayWidth * 5 / 6, cardY,
                            device.displayWidth / 6, cardY, 20)
                }
                assertTrue("Customized must remain reachable in animation cards", device.hasObject(By.text("Customized")))
                device.findObject(By.text("Customized")).click()
                assertTrue("Customized must open real Keyframe Studio", device.wait(Until.hasObject(By.text("Keyframe Studio")), 15_000L))
                for (property in listOf("Position", "Scale", "Rotation", "Opacity")) {
                    assertTrue("Animation inspector must retain $property", device.hasObject(By.text(property)))
                }
                device.findObject(By.desc("Close")).click() // manual inspector returns to cards
                assertTrue(device.wait(Until.hasObject(By.text("Animation")), 10_000L))
                device.findObject(By.desc("Close")).click() // close cards, returning to the timeline
                device.waitForIdle(1_000L)
                // Use the existing expanded workspace to inspect every real material row.
                // Small normal viewports intentionally show a scrollable subset, not empty lanes.
                device.findObject(By.desc("Expand timeline to full height")).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Restore preview and normal timeline layout")), 10_000L))
                val trackBottom = device.findObject(By.desc("Animation")).visibleBounds.top - 40
                val trackScrollStep = (100 * context.resources.displayMetrics.density).toInt()
                val seenLayers = mutableSetOf<String>()
                val expectedLayers = listOf("Main video track", "V2 overlay", "Text", "Stickers", "Music", "Voice and sound effects")
                for (attempt in 0 until 8) {
                    expectedLayers.filterTo(seenLayers) { device.hasObject(By.desc(it)) }
                    if (seenLayers.containsAll(expectedLayers)) break
                    device.swipe(device.displayWidth / 2, trackBottom,
                        device.displayWidth / 2, trackBottom - trackScrollStep, 25)
                }
                assertTrue("All actual material rows must be reachable, found $seenLayers", seenLayers.containsAll(expectedLayers))
                repeat(4) {
                    device.swipe(device.displayWidth / 2, trackBottom - trackScrollStep,
                        device.displayWidth / 2, trackBottom, 25)
                }
                device.findObject(By.desc("Restore preview and normal timeline layout")).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Expand timeline to full height")), 10_000L))
                device.waitForIdle(1_000L)
                device.takeScreenshot(screenshot)
                device.dumpWindowHierarchy(hierarchy)
                val restoredClip = vm.state.value.project!!.clips.first()
                assertEquals("A real keyframe should be added to the selected video", 1,
                    restoredClip.keyframes.keyframes.size)
                assertEquals("Unrelated fixture media identity must be preserved", uri, restoredClip.uri)
                assertEquals("Text overlay must survive keyframe editing", listOf(title), restoredClip.textOverlays)
                assertEquals("Audio tracks must survive keyframe editing", audioTracks, vm.state.value.project!!.audioTracks)
                assertEquals("Stickers must survive keyframe editing", stickers, vm.state.value.project!!.stickers)
            }
        } finally {
            device.unfreezeRotation()
            source.delete()
        }
    }
}
