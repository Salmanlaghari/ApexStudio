package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
            onText = { count("text") },
            onEffects = { count("effects") },
            onStickers = { count("stickers") },
            onArFace = { count("ar-face") },
            onLenses = { count("lenses") },
            onFilters = { count("filters") },
            onAddOverlayImage = { count("add-image") },
            onAddOverlayVideo = { count("add-video") },
            onAdjust = { count("adjust") },
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
