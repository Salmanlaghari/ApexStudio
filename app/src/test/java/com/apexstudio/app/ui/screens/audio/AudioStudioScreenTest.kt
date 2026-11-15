package com.apexstudio.app.ui.screens.audio

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioStudioScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `each EQ slider updates only its own audio band`() {
        val vm = EditorViewModel()
        compose.setContent {
            AudioStudioScreen(
                projectId = "eq-test",
                onBack = {},
                onExport = {},
                vm = vm
            )
        }

        setSlider("audio-eq-low", 0.2f)
        setSlider("audio-eq-mid", -0.35f)
        setSlider("audio-eq-high", 0.65f)

        compose.runOnIdle {
            assertEquals(200.toShort(), vm.audio.value.lowEQ)
            assertEquals((-350).toShort(), vm.audio.value.midEQ)
            assertEquals(650.toShort(), vm.audio.value.highEQ)
        }
    }

    private fun setSlider(tag: String, value: Float) {
        compose.onNodeWithTag(tag)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(value))
            }
    }
}
