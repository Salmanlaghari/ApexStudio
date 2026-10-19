package com.apexstudio.app.ui.screens.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.apexstudio.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoPreviewGeometryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fitAndCoverUseCenteredMinAndMaxScaleWithAccurateNormalizedMapping() {
        val image = IntSize(400, 200)
        val viewport = IntSize(300, 300)

        val fit = photoDisplayRect(image, viewport, isCoverMode = false, cropActive = false)
        assertNotNull(fit)
        assertEquals(0f, fit!!.left, 0.001f)
        assertEquals(75f, fit.top, 0.001f)
        assertEquals(300f, fit.width, 0.001f)
        assertEquals(150f, fit.height, 0.001f)
        // Image-space (25%, 50%) maps into the actual fitted image, not the viewport.
        assertEquals(75f, fit.left + 0.25f * fit.width, 0.001f)
        assertEquals(150f, fit.top + 0.5f * fit.height, 0.001f)

        val cover = photoDisplayRect(image, viewport, isCoverMode = true, cropActive = false)
        assertNotNull(cover)
        assertEquals(-150f, cover!!.left, 0.001f)
        assertEquals(0f, cover.top, 0.001f)
        assertEquals(600f, cover.width, 0.001f)
        assertEquals(300f, cover.height, 0.001f)
        // The viewport's left/right edges correspond to the center 50% of the wider source.
        assertEquals(0f, cover.left + 0.25f * cover.width, 0.001f)
        assertEquals(300f, cover.left + 0.75f * cover.width, 0.001f)
    }

    @Test
    fun cropEditingForcesFitSoEveryHandleAndCropCoordinateStaysVisible() {
        val rect = photoDisplayRect(
            imageSize = IntSize(400, 200),
            viewportSize = IntSize(300, 300),
            isCoverMode = true,
            cropActive = true
        )
        assertNotNull(rect)
        assertEquals(0f, rect!!.left, 0.001f)
        assertEquals(75f, rect.top, 0.001f)
        assertEquals(300f, rect.width, 0.001f)
        assertEquals(150f, rect.height, 0.001f)
        // Full-image crop corners remain inside the viewport for hit testing.
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(300f, rect.right, 0.001f)
        assertEquals(75f, rect.top, 0.001f)
        assertEquals(225f, rect.bottom, 0.001f)
    }

    @Test
    fun recompositionUpdatesTheInflatedNativePlayerViewsActualResizeMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val coverMode = mutableStateOf(true)
        var composeRoot: View? = null
        var playerView: PlayerView? = null
        try {
            compose.setContent {
                val root = LocalView.current
                SideEffect { composeRoot = root }
                VideoPreviewArea(
                    exoPlayer = player,
                    isCoverMode = coverMode.value,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }

            compose.waitForIdle()
            compose.runOnIdle {
                playerView = findPlayerView(composeRoot)
            }
            assertNotNull("VideoPreviewArea should inflate view_player.xml", playerView)
            val capturedPlayerView = checkNotNull(playerView)
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, capturedPlayerView.resizeMode)

            compose.runOnUiThread { coverMode.value = false }
            compose.waitForIdle()
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, capturedPlayerView.resizeMode)

            compose.runOnUiThread { coverMode.value = true }
            compose.waitForIdle()
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, capturedPlayerView.resizeMode)
        } finally {
            compose.runOnUiThread { player.release() }
        }
    }

    private fun findPlayerView(root: View?): PlayerView? {
        root ?: return null
        if (root is PlayerView && root.id == R.id.player_view) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findPlayerView(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    @Test
    fun geometryRejectsEmptyImageOrViewport() {
        assertNull(photoDisplayRect(IntSize.Zero, IntSize(300, 300), true, false))
        assertNull(photoDisplayRect(IntSize(400, 200), IntSize.Zero, true, false))
    }
}
