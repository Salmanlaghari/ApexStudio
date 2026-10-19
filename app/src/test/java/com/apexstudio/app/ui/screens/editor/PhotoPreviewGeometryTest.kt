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
        val coverMode = mutableStateOf(DEFAULT_PREVIEW_COVER_MODE)
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
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, capturedPlayerView.resizeMode)

            compose.runOnUiThread { coverMode.value = true }
            compose.waitForIdle()
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, capturedPlayerView.resizeMode)

            compose.runOnUiThread { coverMode.value = false }
            compose.waitForIdle()
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, capturedPlayerView.resizeMode)
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
    fun videoBoundsUseTheReportedOutputSizeAndPixelAspect() {
        assertEquals(16f / 9f, displayedVideoAspect(1920, 1080, 1f)!!, 0.0001f)
        assertEquals(4f / 3f, displayedVideoAspect(720, 576, 16f / 15f)!!, 0.0001f)
        // A square output remains square; don't multiply its original crop again.
        assertEquals(1f, displayedVideoAspect(1080, 1080, 1f)!!, 0.0001f)
        assertNull(displayedVideoAspect(0, 1080, 1f))
        assertNull(displayedVideoAspect(1920, 0, 1f))
        assertNull(displayedVideoAspect(1920, 1080, Float.NaN))
    }

    @Test
    fun effectOutputAspectWinsOverDecoderAspectForPreviewOverlayBounds() {
        // The face-crop GL effect emits a fixed portrait frame even when the
        // underlying decoder still reports the landscape source dimensions.
        assertEquals(
            9f / 16f,
            previewContentAspect(1920, 1080, 1f, effectOutputAspect = 9f / 16f)!!,
            0.0001f
        )
        // Invalid/missing effect output metadata safely falls back to decoder size.
        assertEquals(16f / 9f, previewContentAspect(1920, 1080, 1f, 0f)!!, 0.0001f)
        assertNull(previewContentAspect(0, 0, 1f, Float.NaN))
    }

    @Test
    fun manualCropOutputAspectUsesCroppedPixelsNotOriginalFrameDimensions() {
        val crop = com.apexstudio.app.domain.model.VideoCropSettings(
            left = 0.25f, top = 0f, right = 0.75f, bottom = 1f
        )
        // Cropping a 16:9 source to its central half yields an 8:9 output.
        assertEquals(8f / 9f, videoCropOutputAspect(1920, 1080, 1f, crop)!!, 0.0001f)
        assertNull(videoCropOutputAspect(1920, 1080, 1f, null))
        assertNull(videoCropOutputAspect(0, 1080, 1f, crop))
    }

    @Test
    fun previewGradeBoundsMatchFitMediaAndCoverViewport() {
        val fit = previewContentBounds(300f, 300f, mediaAspectRatio = 2f, isCoverMode = false)
        assertNotNull(fit)
        assertEquals(0f, fit!!.left, 0.001f)
        assertEquals(75f, fit.top, 0.001f)
        assertEquals(300f, fit.right, 0.001f)
        assertEquals(225f, fit.bottom, 0.001f)

        val cover = previewContentBounds(300f, 300f, mediaAspectRatio = 2f, isCoverMode = true)
        assertNotNull(cover)
        assertEquals(0f, cover!!.left, 0.001f)
        assertEquals(0f, cover.top, 0.001f)
        assertEquals(300f, cover.right, 0.001f)
        assertEquals(300f, cover.bottom, 0.001f)
        assertNull(previewContentBounds(0f, 300f, mediaAspectRatio = 2f, isCoverMode = false))
    }

    @Test
    fun geometryRejectsEmptyImageOrViewport() {
        assertNull(photoDisplayRect(IntSize.Zero, IntSize(300, 300), true, false))
        assertNull(photoDisplayRect(IntSize(400, 200), IntSize.Zero, true, false))
    }
}
