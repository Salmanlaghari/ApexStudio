package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhaseZeroToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Add Image and Add Video dispatch their distinct supplied callbacks`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent {
            testToolbar(ToolbarSelectionKind.NONE, calls)
        }

        composeRule.onNodeWithText("More").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Add Image").performScrollTo().performClick()
        composeRule.onNodeWithText("Add Video").performScrollTo().performClick()

        assertEquals(1, calls["add-image"] ?: 0)
        assertEquals(1, calls["add-video"] ?: 0)
    }

    @Test
    fun `video Trim and Speed dispatch their supplied callbacks`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent {
            testToolbar(ToolbarSelectionKind.VIDEO, calls)
        }

        composeRule.onNodeWithText("Trim").performScrollTo().performClick()
        composeRule.onNodeWithText("Speed").performScrollTo().performClick()

        assertEquals(1, calls["trim"] ?: 0)
        assertEquals(1, calls["speed"] ?: 0)
    }

    @Test
    fun `PHOTO selection retains functional Trim and Split controls`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent {
            testToolbar(ToolbarSelectionKind.PHOTO, calls)
        }

        composeRule.onNodeWithText("Trim").assertExists().performScrollTo().performClick()
        composeRule.onNodeWithText("Split").assertExists().performScrollTo().performClick()

        assertEquals(1, calls["trim"] ?: 0)
        assertEquals(1, calls["split"] ?: 0)
    }

    @Test
    fun `mobile quick tools dispatch music text enhance templates canvas effects stickers captions and filters`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent {
            testToolbar(ToolbarSelectionKind.NONE, calls)
        }

        listOf("Text", "FX", "Stickers", "Filters").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed().performClick()
        }
        composeRule.onNodeWithText("More").assertIsDisplayed().performClick()
        listOf("Music", "Enhance", "Templates", "Canvas", "Subtitles").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().performClick()
        }

        listOf(
            "music", "text", "enhance", "templates", "crop",
            "effects", "stickers", "captions", "filters"
        ).forEach { action -> assertEquals("$action callback", 1, calls[action] ?: 0) }
    }

    @Test fun `global Enhance and video Adjust use independent callbacks`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent { testToolbar(ToolbarSelectionKind.VIDEO, calls) }
        composeRule.onNodeWithText("More tools").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Enhance").performScrollTo().performClick()
        composeRule.onNodeWithText("Clip").performClick()
        composeRule.onNodeWithText("Adjust").performScrollTo().performClick()
        assertEquals(1, calls["enhance"] ?: 0)
        assertEquals(1, calls["adjust"] ?: 0)
    }

    @Test fun `editor wrapper keeps quick Enhance separate from actual Adjust panel`() {
        val vm = com.apexstudio.app.presentation.viewmodel.EditorViewModel()
        val clip = com.apexstudio.app.domain.model.MediaClip("v", "V", "file:///v.mp4", 5000L, trimEndMs = 5000L)
        vm._state.value = vm.state.value.copy(project = com.apexstudio.app.domain.model.Project("p", "P", 5000L, clips = listOf(clip)), selectedClipId = clip.id)
        val picker = com.apexstudio.app.data.picker.MediaPickerHelper(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        var quickEnhance = 0
        composeRule.setContent { EditorBottomToolbarSection(vm.state.value, vm, picker, onQuickEnhance = { quickEnhance++ }) }
        composeRule.onNodeWithText("More tools").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Enhance").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, quickEnhance); assertEquals(false, vm.state.value.adjustmentsPanelOpen) }
        composeRule.onNodeWithText("Clip").performClick()
        composeRule.onNodeWithText("Adjust").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, quickEnhance); assertEquals(true, vm.state.value.adjustmentsPanelOpen) }
    }

    @Test
    fun `horizontal swipe reveals More actions and tabs navigate back to primary tools`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent { testToolbar(ToolbarSelectionKind.NONE, calls) }

        composeRule.onNodeWithTag("editor_feature_pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Music").assertIsDisplayed().performClick()
        assertEquals(1, calls["music"] ?: 0)
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Text").assertIsDisplayed().performClick()
        assertEquals(1, calls["text"] ?: 0)
    }

    @Test fun approvedPrimaryDockAndOverlayChoicesDispatchRealRoutes() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent { testToolbar(ToolbarSelectionKind.NONE, calls) }
        listOf("Media", "Audio", "FX", "Filters").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed().performClick()
        }
        composeRule.onNodeWithText("Overlay").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Image").performClick()
        composeRule.onNodeWithText("Overlay").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Video").performClick()
        listOf("media", "device-audio", "effects", "filters", "add-image", "add-video").forEach {
            assertEquals(it, 1, calls[it] ?: 0)
        }
    }

    /** Explicit test-only callbacks keep every production action wired and observable. */
    @Composable
    private fun testToolbar(
        selectionKind: ToolbarSelectionKind,
        calls: MutableMap<String, Int>
    ) {
        fun count(name: String) {
            calls[name] = (calls[name] ?: 0) + 1
        }

        ContextualBottomToolbar(
            selectionKind = selectionKind,
            onMedia = { count("media") },
            onDeviceAudio = { count("device-audio") },
            onText = { count("text") },
            onEffects = { count("effects") },
            onStickers = { count("stickers") },
            onCaptions = { count("captions") },
            onTemplates = { count("templates") },
            onCrop = { count("crop") },
            onArFace = { count("ar-face") },
            onLenses = { count("lenses") },
            onFilters = { count("filters") },
            onAddOverlayImage = { count("add-image") },
            onAddOverlayVideo = { count("add-video") },
            onAdjust = { count("adjust") },
            onEnhance = { count("enhance") },
            onReplace = { count("replace") },
            onSpeed = { count("speed") },
            onVolume = { count("volume") },
            onAnimation = { count("animation") },
            onDelete = { count("delete") },
            onBackground = { count("background") },
            onFade = { count("fade") },
            onBeats = { count("beats") },
            onPhotoCrop = { count("photo-crop") },
            onPhotoAdjust = { count("photo-adjust") },
            onPhotoFilters = { count("photo-filters") },
            onPhotoRotate = { count("photo-rotate") },
            onMusic = { count("music") },
            onAutoClip = { count("auto-clip") },
            onHistory = { count("history") },
            onEditPack = { count("edit-pack") },
            onScopes = { count("scopes") },
            onBgRemover = { count("bg-remover") },
            onCollapse = { count("collapse") },
            onSplit = { count("split") },
            onTrim = { count("trim") },
            onMask = { count("mask") },
            onDuplicate = { count("duplicate") },
            onToggleKeyframe = { count("keyframe") },
            onOverlayProps = { count("overlay-props") },
            onAudioMix = { count("audio-mix") },
            onRecord = { count("record") },
            onRoyaltyMusic = { count("royalty-music") },
            onEditText = { count("edit-text") },
            onToggleTextKeyframe = { count("text-keyframe") },
            onDeleteText = { count("delete-text") },
            onDeleteSticker = { count("delete-sticker") },
            onTransition = { count("transition") },
            onToggleLayerVisibility = { count("layer-visibility") },
            onToggleLayerLock = { count("layer-lock") },
            onToggleTrackMute = { count("track-mute") }
        )
    }
}
