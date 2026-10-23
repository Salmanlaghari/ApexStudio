package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextDurationRangeCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wholeTimelineUsesActualProjectDuration() {
        var range: Pair<Long, Long>? = null
        compose.setContent {
            Box(Modifier.width(340.dp)) {
                TextDurationRangeCard(TextOverlay(id = "title", startMs = 1000L, endMs = 3000L), 12000L, 7000L,
                    listOf("First" to 5000L, "Second" to 12000L)) { a, b -> range = a to b }
            }
        }
        compose.onNodeWithText("Whole timeline").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0L to 12000L, range) }
    }

    @Test fun clipEndChoiceCanExtendTextAcrossClipCut() {
        var range: Pair<Long, Long>? = null
        compose.setContent {
            Box(Modifier.width(340.dp)) {
                TextDurationRangeCard(TextOverlay(id = "title", startMs = 1000L, endMs = 3000L), 12000L, 7000L,
                    listOf("First" to 5000L, "Second" to 12000L)) { a, b -> range = a to b }
            }
        }
        compose.onNodeWithText("Through clip 2", substring = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1000L to 12000L, range) }
        compose.onNodeWithTag("text-selected-duration").assertTextContains("00:11", substring = true)
    }

    @Test fun manualKeyframesAcrossCutsExplainTheExportBlocker() {
        val overlay = TextOverlay(id = "title", startMs = 1000L, endMs = 7000L,
            keyframes = com.apexstudio.app.domain.model.KeyframeTrack(listOf(
                com.apexstudio.app.domain.model.Keyframe(id = "pin", timeMs = 1500L))))
        compose.setContent {
            Box(Modifier.width(340.dp)) {
                TextDurationRangeCard(overlay, 12000L, 2000L,
                    listOf("First" to 5000L, "Second" to 12000L)) { _, _ -> }
            }
        }
        compose.onNodeWithText("Manual text/mask keyframes across cuts", substring = true).assertIsDisplayed()
    }

    @Test fun zeroDurationTimelineCannotApplyPlaceholderRange() {
        compose.setContent {
            TextDurationRangeCard(TextOverlay(id = "title"), 0L, 0L, emptyList()) { _, _ -> error("No timeline") }
        }
        compose.onNodeWithText("Whole timeline").assertIsNotEnabled()
        compose.onNodeWithTag("text-project-range-slider").assertIsNotEnabled()
    }
}
