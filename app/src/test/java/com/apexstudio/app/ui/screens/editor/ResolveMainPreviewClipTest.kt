package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveMainPreviewClipTest {
    private fun clip(id: String, type: ClipType, track: Int) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = type,
        trackIndex = track,
    )

    private val v1 = clip("v1", ClipType.VIDEO, 0)
    private val v1Image = clip("v1-image", ClipType.IMAGE, 0)
    private val imageOverlay = clip("image-overlay", ClipType.OVERLAY, 2)
    private val videoOverlay = clip("video-overlay", ClipType.OVERLAY, 3)
    private val audio = clip("audio", ClipType.AUDIO, 0)

    @Test
    fun selectedImageOverlayDoesNotReplaceV1Preview() {
        assertEquals(v1, resolveMainPreviewClip(listOf(v1, imageOverlay), imageOverlay.id))
    }

    @Test
    fun selectedV1VideoOrImageIsUsedByMainPreview() {
        assertEquals(v1Image, resolveMainPreviewClip(listOf(v1, v1Image), v1Image.id))
    }

    @Test
    fun selectedOverlayOrAudioFallsBackToV1AndAudioAloneIsIgnored() {
        val clips = listOf(v1, videoOverlay, audio)
        assertEquals(v1, resolveMainPreviewClip(clips, videoOverlay.id))
        assertEquals(v1, resolveMainPreviewClip(clips, audio.id))
        assertNull(resolveMainPreviewClip(listOf(audio), audio.id))
    }
}
