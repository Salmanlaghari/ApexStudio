package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class MaskPreviewOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun draggingMaskBodyReportsLiveAndFinalCenter() {
        val mask = mutableStateOf(VideoMask(shape = MaskShape.RECTANGLE, centerX = .5f, centerY = .5f))
        var live: Pair<Float, Float>? = null
        var finished: Pair<Float, Float>? = null
        val updates = mutableListOf<Pair<Float, Float>>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp, 240.dp)) {
                    MaskPreviewOverlay(
                        mask = mask.value,
                        editMode = true,
                        onCenterChangeLive = { x, y ->
                            live = x to y
                            updates += x to y
                            mask.value = mask.value!!.withCenter(x, y)
                        },
                        onCenterChangeFinished = { x, y -> finished = x to y }
                    )
                }
            }
        }

        compose.onNodeWithTag("mask-preview-overlay").performTouchInput {
            down(Offset(160f, 120f))
            moveTo(Offset(200f, 140f), delayMillis = 100L)
            moveTo(Offset(220f, 160f), delayMillis = 100L)
            up()
        }

        compose.runOnIdle {
            assertNotNull("body drag emits live updates", live)
            assertTrue("body drag moves right and down after touch slop", live!!.first > .5f && live!!.second > .5f)
            assertTrue("both drag events are delivered", updates.size >= 2)
            // Only the first event consumes touch slop; later deltas must accumulate.
            assertEquals(20f / 320f, updates.last().first - updates[updates.lastIndex - 1].first, .001f)
            assertEquals(20f / 240f, updates.last().second - updates[updates.lastIndex - 1].second, .001f)
            assertEquals("release commits the same final center", live, finished)
        }
    }

    @Test
    fun draggingResizeHandleReportsLiveAndFinalSize() {
        val mask = mutableStateOf(VideoMask(shape = MaskShape.RECTANGLE, centerX = .5f, centerY = .5f, sizeX = .5f, sizeY = .5f))
        var live: Pair<Float, Float>? = null
        var finished: Pair<Float, Float>? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(320.dp, 240.dp)) {
                    MaskPreviewOverlay(
                        mask = mask.value,
                        editMode = true,
                        onSizeChangeLive = { sx, sy ->
                            live = sx to sy
                            mask.value = mask.value!!.withSize(sx, sy)
                        },
                        onSizeChangeFinished = { sx, sy -> finished = sx to sy }
                    )
                }
            }
        }

        // For the initial mask, the lower-right resize handle is at (240, 180).
        compose.onNodeWithTag("mask-preview-overlay").performTouchInput {
            down(Offset(240f, 180f))
            moveTo(Offset(280f, 200f), delayMillis = 100L)
            up()
        }

        compose.runOnIdle {
            assertNotNull("handle drag emits live updates", live)
            assertTrue(live!!.first > .5f)
            assertTrue(live!!.second > .5f)
            assertEquals("release commits the same final size", live, finished)
        }
    }
}
