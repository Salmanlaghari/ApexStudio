package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenshotClipToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun premiumDockFitsAllEightRealToolsWithoutHorizontalClippingAt320dp() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent {
            Box(Modifier.width(320.dp)) { testToolbar(calls, selectionKind = ToolbarSelectionKind.NONE) }
        }
        val bounds = CONTEXTUAL_PRIMARY_TOOL_ORDER.map { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue(bounds.zipWithNext().all { (left, right) -> left.right <= right.left })
        assertTrue("More must not be clipped at the right edge", bounds.last().right <= 320f)
        listOf("Media" to "media", "Audio" to "device-audio", "Text" to "text",
            "Stickers" to "stickers", "FX" to "effects", "Filters" to "filters").forEach { (label, action) ->
            composeRule.onNodeWithText(label).performClick()
            assertEquals("$label must keep its real callback", 1, calls[action])
        }
    }

    @Test
    fun `selected video shows the screenshot priority tools and keeps primary navigation visible`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent { testToolbar(calls) }

        CONTEXTUAL_VIDEO_PRIORITY_TOOL_ORDER.forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        val renderedLeftEdges = CONTEXTUAL_VIDEO_PRIORITY_TOOL_ORDER.take(4).map { label ->
            composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.left
        }
        assertTrue(
            "Screenshot-priority tools must render left-to-right in the approved order: $renderedLeftEdges",
            renderedLeftEdges.zipWithNext().all { (left, right) -> left < right }
        )
        // The fifth tool can start outside a narrow viewport, as in the
        // reference. Bring it into view before comparing clipped bounds.
        composeRule.onNodeWithText("Keyframe").performScrollTo()
        assertTrue(composeRule.onNodeWithText("Volume").fetchSemanticsNode().boundsInRoot.left <
            composeRule.onNodeWithText("Keyframe").fetchSemanticsNode().boundsInRoot.left)
        // Global actions live on the adjacent Tools page, reachable by swipe
        // or its persistent tab; they are intentionally not stacked below Clip.
        composeRule.onNodeWithText("Tools").assertIsDisplayed().performClick()
        listOf("Media", "Audio", "Text", "Overlay", "Stickers", "FX", "Filters", "More")
            .forEach { label ->
                assertTrue("Primary navigation retains $label",
                    composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
            }
        composeRule.onNodeWithText("Clip").performClick()

        listOf(
            "Split" to "split",
            "Trim" to "trim",
            "Speed" to "speed",
            "Volume" to "volume",
            "Keyframe" to "keyframe"
        ).forEach { (label, action) ->
            composeRule.onNodeWithText(label).performScrollTo().performClick()
            assertEquals("$label callback", 1, calls[action] ?: 0)
        }
    }

    @Test
    fun `video-specific extras remain scrollable and More retains global tools`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent { testToolbar(calls) }

        listOf("Animation" to "animation", "Replace" to "replace", "Duplicate" to "duplicate")
            .forEach { (label, action) ->
                composeRule.onNodeWithText(label).performScrollTo().performClick()
                assertEquals("$label callback", 1, calls[action] ?: 0)
            }

        composeRule.onNodeWithText("More tools").performClick()
        composeRule.onNodeWithText("Camera").assertDoesNotExist()
        composeRule.onNodeWithText("Lenses").assertDoesNotExist()
        composeRule.onNodeWithText("Transition").performScrollTo().assertExists()
        composeRule.onNodeWithText("History").performScrollTo().assertExists()
    }

    @Test
    fun `overlay volume opens overlay properties and unsupported speed is not advertised`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent {
            testToolbar(calls, isOverlaySelected = true)
        }

        composeRule.onNodeWithText("Speed").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Editing and export notes").performClick()
        composeRule.onNodeWithText("Overlay speed editing is not available yet; use Properties for overlay volume.").assertExists()
        composeRule.onNodeWithText("Got it").performClick()
        composeRule.onNodeWithText("Volume").performScrollTo().performClick()
        assertEquals(1, calls["overlay-props"] ?: 0)
        assertEquals(0, calls["volume"] ?: 0)
    }

    @Test
    fun `legacy audio keeps clip actions but omits track-only actions with an explanation`() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent {
            testToolbar(calls, selectionKind = ToolbarSelectionKind.AUDIO, isLegacyAudioClipSelected = true)
        }

        listOf("Fade", "Beats", "Volume", "Mute").forEach { label ->
            composeRule.onNodeWithText(label).assertDoesNotExist()
        }
        composeRule.onNodeWithContentDescription("Editing and export notes").performClick()
        composeRule.onNodeWithText("Fade, Beats, Mute, and Volume are available for project audio tracks only.").assertExists()
        composeRule.onNodeWithText("Got it").performClick()
        listOf("Trim" to "trim", "Replace" to "replace", "Delete" to "delete").forEach { (label, action) ->
            composeRule.onNodeWithText(label).performScrollTo().performClick()
            assertEquals("$label remains a real clip action", 1, calls[action] ?: 0)
        }
        assertEquals(0, calls["fade"] ?: 0)
        assertEquals(0, calls["beats"] ?: 0)
        assertEquals(0, calls["volume"] ?: 0)
        assertEquals(0, calls["track-mute"] ?: 0)
    }

    @Test fun selectedActionsRemainAvailableThroughTheClipTabEvenWhenToolsIsInitialPage() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent { testToolbar(calls, showSelectionToolsInitially = false) }
        composeRule.onNodeWithText("Split").assertDoesNotExist()
        composeRule.onNodeWithText("Clip").performClick()
        composeRule.onNodeWithText("Split").performScrollTo().performClick()
        assertEquals(1, calls["split"])
    }

    @Test fun horizontalSwipeRevealsNextWorkspaceAndTabsProvideTapNavigation() {
        val calls = mutableMapOf<String, Int>()
        composeRule.setContent { testToolbar(calls) }
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithTag("editor_feature_pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Music").assertIsDisplayed()
        composeRule.onNodeWithText("Music").assertIsDisplayed()
        composeRule.onNodeWithText("Clip").performClick()
        composeRule.onNodeWithText("Split").performScrollTo().performClick()
        assertEquals(1, calls["split"] ?: 0)
    }

    @Composable
    private fun testToolbar(
        calls: MutableMap<String, Int>,
        selectionKind: ToolbarSelectionKind = ToolbarSelectionKind.VIDEO,
        isOverlaySelected: Boolean = false,
        isLegacyAudioClipSelected: Boolean = false,
        showSelectionToolsInitially: Boolean = true
    ) {
        fun count(action: String) {
            calls[action] = (calls[action] ?: 0) + 1
        }

        ContextualBottomToolbar(
            selectionKind = selectionKind,
            isOverlaySelected = isOverlaySelected,
            isLegacyAudioClipSelected = isLegacyAudioClipSelected,
            showSelectionToolsInitially = showSelectionToolsInitially,
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
