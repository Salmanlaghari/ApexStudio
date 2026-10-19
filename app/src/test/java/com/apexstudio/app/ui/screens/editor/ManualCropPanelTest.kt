package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.VideoCropSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualCropPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sliderChangesOnlyTheDraftAndApplyEmitsOneCommittedValue() {
        val commits = mutableListOf<VideoCropSettings>()
        val initial = VideoCropSettings(.1f, .2f, .8f, .9f, "4:5")
        compose.setContent {
            Box(Modifier.width(400.dp)) { ManualCropPanel(initial, commits::add, {}) }
        }
        compose.onNodeWithContentDescription("Left crop position").performSemanticsAction(SemanticsActions.SetProgress) { it(.25f) }
        compose.runOnIdle { assertTrue(commits.isEmpty()) }
        compose.onNodeWithText("Apply crop").performClick()
        compose.runOnIdle {
            assertEquals(1, commits.size)
            assertEquals(.25f, commits.single().left, .00001f)
            assertEquals(initial.right, commits.single().right, 0f)
            assertEquals("free", commits.single().presetId)
        }
    }

    @Test fun cancelAfterEditingDoesNotEmitAnyCropAndFullFrameRemainsADraft() {
        val commits = mutableListOf<VideoCropSettings>()
        var cancellations = 0
        compose.setContent {
            Box(Modifier.width(400.dp)) { ManualCropPanel(VideoCropSettings(.2f, .2f, .8f, .8f), commits::add, { cancellations++ }) }
        }
        compose.onNodeWithText("Full frame").performClick()
        compose.runOnIdle { assertTrue(commits.isEmpty()) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(1, cancellations)
            assertTrue(commits.isEmpty())
        }
    }
}
