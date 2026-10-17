package com.apexstudio.app.presentation.state

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bug 5 — the Keyframe / Animation panel must target the VIDEO layer the
 * user selected, and nothing else.
 *
 * The regression being pinned: the overlay used to resolve its target as
 * `clips.firstOrNull { it.id == selectedClipId } ?: clips.firstOrNull()`,
 * so a cleared or stale selection silently re-pointed the panel at the
 * FIRST clip in the project. A slider change then calls
 * `upsertKeyframeParam` / `addKeyframe` against that clip — an animated
 * property written onto a layer nobody had selected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyframeAnchorTest {

    private fun clip(id: String, type: ClipType) = MediaClip(
        id = id,
        name = "$type.mp4",
        uri = "content://media/$id",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = type
    )

    private fun state(clips: List<MediaClip>, selectedId: String?): EditorState =
        EditorState(
            project = Project(id = "p", name = "p", durationMs = 10_000L, clips = clips),
            selectedClipId = selectedId
        )

    private val clips = listOf(
        clip("v1", ClipType.VIDEO),
        clip("o1", ClipType.OVERLAY),
        clip("i1", ClipType.IMAGE),
        clip("a1", ClipType.AUDIO)
    )

    @Test
    fun `selected video layer is the anchor`() {
        val anchor = state(clips, "v1").keyframeAnchor()
        assertNotNull(anchor)
        assertSame(clips[0], anchor)
    }

    @Test
    fun `selected overlay layer is the anchor`() {
        val anchor = state(clips, "o1").keyframeAnchor()
        assertNotNull(anchor)
        assertSame(clips[1], anchor)
    }

    @Test
    fun `selected image layer is the anchor`() {
        val anchor = state(clips, "i1").keyframeAnchor()
        assertNotNull(anchor)
        assertSame(clips[2], anchor)
    }

    @Test
    fun `cleared selection yields no anchor instead of falling back to the first clip`() {
        // The bug: `?: clips.firstOrNull()` would have returned clips[0].
        assertNull(
            "A deselected timeline must never retarget the keyframe panel",
            state(clips, null).keyframeAnchor()
        )
    }

    @Test
    fun `stale selection id yields no anchor`() {
        assertNull(state(clips, "clip-deleted-ago").keyframeAnchor())
    }

    @Test
    fun `audio clips are not anchored by the video keyframe controls`() {
        // Volume for audio is driven by the mixer lane, not this panel.
        assertNull(state(clips, "a1").keyframeAnchor())
    }

    @Test
    fun `empty project yields no anchor`() {
        assertNull(
            EditorState(
                project = Project(id = "p", name = "p", durationMs = 0L),
                selectedClipId = "v1"
            ).keyframeAnchor()
        )
    }

    @Test
    fun `null project yields no anchor`() {
        assertNull(EditorState(selectedClipId = "v1").keyframeAnchor())
    }

    @Test
    fun `isKeyframeTarget matches the panel contract`() {
        assertSame(true, clip("t", ClipType.VIDEO).isKeyframeTarget())
        assertSame(true, clip("t", ClipType.OVERLAY).isKeyframeTarget())
        assertSame(true, clip("t", ClipType.IMAGE).isKeyframeTarget())
        assertSame(false, clip("t", ClipType.AUDIO).isKeyframeTarget())
        assertSame(false, clip("t", ClipType.SFX).isKeyframeTarget())
    }
}
