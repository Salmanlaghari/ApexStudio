package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Size
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.components.VideoTimeline
import com.apexstudio.app.ui.theme.EditorPalette
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziComposeSizeOption
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Host-side visual QA only: captures production Compose editor slices in a
 * synthetic, explicitly test-only project fixture. This is NOT a phone/device
 * screenshot or a rendering claim for actual user media. The preview intentionally
 * has no ExoPlayer source; the timeline's filmstrip is a generated test bitmap,
 * never a user screenshot or project background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockedEditorVisualRenderTest {
    @Test
    fun lockedEditorProductionSlicesRenderAtPhonePortraitHostSize() {
        val geometry = VisualGeometry()
        val output = File("build/outputs/visual/locked-editor-host.png")
        output.parentFile?.mkdirs()

        captureRoboImage(
            output,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions {
                addOption(RoborazziComposeSizeOption(widthDp = 375, heightDp = 830))
            }
        ) {
            LockedEditorVisualFixture(geometry)
        }

        // Use ratios/tolerances rather than relying on a density-specific raw pixel size.
        val preview = requireNotNull(geometry.preview) { "Preview did not receive layout" }
        val previewRatio = preview.width / preview.height
        assertEquals("Preview should retain the locked landscape ~1.36 ratio", 1.36f, previewRatio, 0.04f)

        val timeline = requireNotNull(geometry.timeline) { "Timeline did not receive layout" }
        assertTrue("Timeline should have a compact viewport, not a second title/header row", timeline.height in 180f..380f)
        assertTrue("Timeline should sit below the keyframe controls: timeline=$timeline keyframe=${geometry.keyframe}", timeline.top + 1f >= requireNotNull(geometry.keyframe).bottom)

        val dock = requireNotNull(geometry.primaryDock) { "Primary toolbar dock did not receive layout" }
        assertTrue("Primary tool dock should be a horizontal navigation strip", dock.width > dock.height * 3f)
        val keyframe = requireNotNull(geometry.keyframe)
        val screenCenterX = requireNotNull(geometry.root).width / 2f
        assertTrue("Keyframe controls should be centered above the timeline", kotlin.math.abs(keyframe.centerX - screenCenterX) < screenCenterX * 0.06f)
        assertTrue("Roborazzi should have written the host render", output.isFile && output.length() > 0L)
    }
}

private class VisualGeometry {
    var root: RectPx? = null
    var preview: RectPx? = null
    var keyframe: RectPx? = null
    var timeline: RectPx? = null
    var primaryDock: RectPx? = null
}

private data class RectPx(val left: Float, val top: Float, val width: Float, val height: Float) {
    val bottom get() = top + height
    val centerX get() = left + width / 2f
}

private fun LayoutCoordinates.toRectPx(): RectPx {
    val position = positionInRoot()
    val size = size.toSize()
    return RectPx(position.x, position.y, size.width, size.height)
}

@Composable
private fun LockedEditorVisualFixture(geometry: VisualGeometry) {
    val density = LocalDensity.current
    val clip = MediaClip(
        id = "fixture-main-video",
        name = "Cyan hour / sample take",
        uri = "file:///test-fixture/main-video.mp4",
        durationMs = 24_000L,
        trimEndMs = 24_000L,
        keyframes = KeyframeTrack(
            listOf(
                Keyframe(id = "kf-start", timeMs = 2_000L, scale = 1f),
                Keyframe(id = "kf-punch-in", timeMs = 9_000L, scale = 1.14f, translateX = 0.04f)
            )
        )
    )
    val textOverlay = TextOverlay(
        id = "fixture-title",
        text = "MIDNIGHT / 04:12",
        x = 0.5f,
        y = 0.28f,
        colorArgb = 0xFFFFFFFFL,
        isBold = true,
        startMs = 1_000L,
        endMs = 15_000L,
        keyframes = KeyframeTrack(listOf(Keyframe(id = "text-keyframe", timeMs = 7_000L, scale = 1.08f)))
    )
    val sticker = StickerOverlay(
        id = "fixture-sticker",
        symbolOrUri = "✦",
        name = "Star sample",
        x = 0.78f,
        y = 0.58f,
        startMs = 3_000L,
        endMs = 18_000L,
        keyframes = KeyframeTrack(listOf(Keyframe(id = "sticker-keyframe", timeMs = 8_000L, rotationDeg = 8f)))
    )
    val music = AudioTrack(
        id = "fixture-music",
        name = "Glass City — instrumental",
        uri = "file:///test-fixture/glass-city.wav",
        trimEndMs = 24_000L,
        sourceDurationMs = 24_000L,
        volume = 0.72f,
        kind = AudioTrack.Kind.MUSIC
    )
    val filmstrip = (0L..8_000L step 1_000L).associate { timeMs ->
        (clip.id to timeMs) to testFilmstripBitmap(timeMs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorPalette.BgDeep)
            .onGloballyPositioned { geometry.root = it.toRectPx() },
        verticalArrangement = Arrangement.Top
    ) {
        TopAppBarSection(
            title = "Cyan hour / sample take",
            canUndo = true,
            canRedo = true,
            resolution = "1080P",
            projectId = "fixture-locked-51303"
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .aspectRatio(LOCKED_EDITOR_PREVIEW_ASPECT)
                .onGloballyPositioned { geometry.preview = it.toRectPx() }
        ) {
            // Deliberately exercise the production empty-player preview state:
            // no imported/user video frame is used for the host capture.
            VideoPreviewArea(modifier = Modifier.fillMaxSize())
        }

        PlaybackControlBar(
            currentTimeMs = 7_000L,
            durationMs = 24_000L,
            canUndo = true,
            canRedo = true,
            showInlineKeyframe = false,
            onTogglePlay = {},
            onUndo = {},
            onRedo = {}
        )

        Box(
            Modifier.fillMaxWidth().height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            SelectedVideoKeyframeControl(
                hasKeyframeAtPlayhead = true,
                onToggleKeyframe = {},
                onOpenAnimation = {},
                modifier = Modifier.onGloballyPositioned { geometry.keyframe = it.toRectPx() }
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { geometry.timeline = it.toRectPx() }
        ) {
            VideoTimeline(
                clips = listOf(clip),
                audioTracks = listOf(music),
                textOverlays = listOf(textOverlay),
                stickers = listOf(sticker),
                totalDurationMs = 24_000L,
                playheadMs = 7_000L,
                selectedClipId = clip.id,
                filmstripFrames = filmstrip,
                frameIntervalMs = 1_000L,
                onOpenSpeed = {},
                onOpenTrim = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp)
                .onGloballyPositioned { geometry.primaryDock = it.toRectPx() }
        ) {
            ContextualBottomToolbar(
                selectionKind = ToolbarSelectionKind.NONE,
                onText = {}, onEffects = {}, onStickers = {}, onArFace = {}, onLenses = {},
                onFilters = {}, onAddOverlayImage = {}, onAddOverlayVideo = {},
                onAdjust = {}, onReplace = {}, onSpeed = {}, onVolume = {}, onAnimation = {},
                onDelete = {}, onBackground = {}, onFade = {}, onBeats = {},
                onPhotoCrop = {}, onPhotoAdjust = {}, onPhotoFilters = {}, onPhotoRotate = {},
                onMusic = {}, onAutoClip = {}, onHistory = {}, onEditPack = {}, onScopes = {},
                onBgRemover = {}, onCollapse = {}, onSplit = {}, onTrim = {}, onMask = {},
                onDuplicate = {}, onToggleKeyframe = {}, onOverlayProps = {}, onAudioMix = {},
                onRecord = {}, onRoyaltyMusic = {}, onEditText = {}, onToggleTextKeyframe = {},
                onDeleteText = {}, onDeleteSticker = {}, onTransition = {},
                onToggleLayerVisibility = {}, onToggleLayerLock = {}, onToggleTrackMute = {}
            )
        }
    }

    // Keep this reference in the test so the assumed fixture host density is visible in a debugger.
    @Suppress("UNUSED_VARIABLE") val fixtureDensity = with(density) { 1.dp.toPx() }
}

/** Small deterministic cyan/purple bitmap samples; test art, not extracted media. */
private fun testFilmstripBitmap(atMs: Long): Bitmap {
    val bitmap = Bitmap.createBitmap(96, 56, Bitmap.Config.ARGB_8888)
    val phase = (atMs / 1_000L).toInt()
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val color = when {
                y < 28 && x < 48 -> if ((x + phase * 5) % 20 < 10) 0xFF156A82.toInt() else 0xFF28B8C9.toInt()
                y < 28 -> if ((x + phase * 7) % 24 < 12) 0xFF67369C.toInt() else 0xFFB36DDF.toInt()
                x < 48 -> if ((x + phase * 3) % 16 < 8) 0xFF263B62.toInt() else 0xFF567AA0.toInt()
                else -> if ((x + phase * 4) % 18 < 9) 0xFF6D326F.toInt() else 0xFFCB65B7.toInt()
            }
            bitmap.setPixel(x, y, color)
        }
    }
    return bitmap
}
