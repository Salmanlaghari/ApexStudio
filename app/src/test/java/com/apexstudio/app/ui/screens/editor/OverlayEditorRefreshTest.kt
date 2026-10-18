package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayEditorRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sameIdTrimAndDurationRefreshCutCallbacksAndKeepLayoutEditsLossless() {
        var clip by mutableStateOf(
            MediaClip(
                id = "overlay-1",
                name = "overlay.mp4",
                uri = "content://media/overlay",
                durationMs = 10_000L,
                trimStartMs = 1_000L,
                trimEndMs = 8_000L,
                type = ClipType.OVERLAY,
                pipX = 0.4f,
            )
        )
        val trimCallbacks = mutableListOf<Pair<Long, Long>>()
        val layoutCallbacks = mutableListOf<Float>()

        compose.setContent {
            OverlayEditor(
                clip = clip,
                onLayout = { x, y, scale, rotation ->
                    layoutCallbacks += x
                    clip = clip.copy(
                        pipX = x,
                        pipY = y,
                        pipScale = scale,
                        pipRotationDeg = rotation,
                    )
                },
                onTrim = { start, end ->
                    trimCallbacks += start to end
                    clip = clip.copy(trimStartMs = start, trimEndMs = end)
                },
            )
        }

        // A trim/duration update arrives for the same clip ID after this editor
        // has already been composed (e.g. a committed trim or source refresh).
        compose.runOnIdle {
            clip = clip.copy(
                durationMs = 12_000L,
                trimStartMs = 3_000L,
                trimEndMs = 9_000L,
            )
        }
        compose.onNodeWithText("Cut start").assertExists()
        compose.onNodeWithText("00:03.00").assertExists()
        compose.onNodeWithText("00:09.00").assertExists()
        compose.onNodeWithText("00:06.00 of 00:12.00 kept").assertExists()

        // Cut end uses the refreshed source duration and keeps the refreshed
        // start point when it commits.
        compose.onNodeWithContentDescription("Cut end")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(10_000f) }
        compose.runOnIdle {
            assertEquals(listOf(3_000L to 10_000L), trimCallbacks)
            assertEquals(3_000L, clip.trimStartMs)
            assertEquals(10_000L, clip.trimEndMs)
        }

        // A geometry callback must use the refreshed model without resetting
        // its newly supplied source window.
        compose.onNodeWithContentDescription("Position X")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.7f) }
        compose.runOnIdle {
            assertEquals(0.7f, layoutCallbacks.last(), 0.001f)
            assertEquals(0.7f, clip.pipX, 0.001f)
            assertEquals(3_000L, clip.trimStartMs)
            assertEquals(10_000L, clip.trimEndMs)
            assertEquals(listOf(3_000L to 10_000L), trimCallbacks)
        }

        // The cut-start callback is clamped against the refreshed end, not
        // the stale end from the first composition.
        compose.onNodeWithContentDescription("Cut start")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5_000f) }
        compose.runOnIdle {
            assertEquals(listOf(3_000L to 10_000L, 5_000L to 10_000L), trimCallbacks)
            assertEquals(5_000L, clip.trimStartMs)
            assertEquals(10_000L, clip.trimEndMs)
        }
        compose.onNodeWithText("00:05.00 of 00:12.00 kept")
            .assertTextEquals("00:05.00 of 00:12.00 kept")
    }
}
