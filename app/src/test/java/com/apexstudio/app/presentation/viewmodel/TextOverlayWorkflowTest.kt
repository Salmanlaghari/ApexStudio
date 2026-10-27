package com.apexstudio.app.presentation.viewmodel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.components.mapProjectTextLayers
import com.apexstudio.app.ui.screens.editor.ScreenWordingObjects
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextOverlayWorkflowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun createEditStyleMoveFeedsPreviewAndTimelineFromTheSameOverlay() {
        val clip = MediaClip(
            id = "main",
            name = "Main video",
            uri = "file:///main.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            type = ClipType.VIDEO
        )
        val vm = EditorViewModel().apply {
            _state.update { it.copy(
                project = Project("text-workflow", "Text workflow", 10_000L, clips = listOf(clip)),
                selectedClipId = clip.id,
                durationMs = 10_000L
            ) }
        }

        vm.addTextOverlay(clip.id, text = "First draft")
        val textId = vm.state.value.selectedTextOverlayId!!
        vm.setTextOverlayText(clip.id, textId, "Edited caption")
        vm.setTextOverlayStyle(clip.id, textId, isBold = true, isItalic = true)
        vm.setTextOverlaySize(clip.id, textId, 1.4f)
        vm.moveTextOverlay(clip.id, textId, dx = 0.15f, dy = 0.2f)

        val savedClip = vm.state.value.project!!.clips.single()
        val overlay = savedClip.textOverlays.single()
        assertEquals("Edited caption", overlay.text)
        assertTrue(overlay.isBold && overlay.isItalic)
        assertEquals(1.4f, overlay.sizeScale, 0.001f)
        assertEquals(0.65f, overlay.x, 0.001f)
        assertEquals(0.55f, overlay.y, 0.001f)
        assertEquals(textId, vm.state.value.selectedTextOverlayId)

        val timelineLayers = mapProjectTextLayers(
            clips = vm.state.value.project!!.clips,
            overlays = vm.state.value.project!!.clips.flatMap { it.textOverlays },
            ownerByOverlayId = mapOf(overlay.id to clip.id)
        )
        assertEquals(1, timelineLayers.size)
        assertEquals(clip.id, timelineLayers.single().ownerClipId)
        assertEquals("Edited caption", timelineLayers.single().overlay.text)

        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                ScreenWordingObjects(
                    stickers = emptyList(),
                    textOverlays = listOf(overlay),
                    coverText = null,
                    coverTextStyle = null,
                    selectedTextOverlayId = null,
                    currentTimeMs = 0L,
                    isPlaying = false,
                    modifier = Modifier.size(360.dp, 640.dp)
                )
            }
        }
        compose.onNodeWithText("Edited caption").assertIsDisplayed()
    }
}
