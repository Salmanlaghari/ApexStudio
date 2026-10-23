package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.BackgroundReplaceMode
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy tests; no Android runtime or Gradle build is required here. */
class EditorBackgroundControllerTest {
    private val original = BackgroundApplyVersion(
        projectId = "project-a",
        clipId = "clip-a",
        uri = "content://media/original.mp4",
        backgroundReplace = null
    )

    @Test
    fun `unchanged originating clip can accept result`() {
        assertTrue(backgroundApplyCanCommit(original, original.copy()))
    }

    @Test
    fun `result is rejected after switching project`() {
        assertFalse(backgroundApplyCanCommit(original, original.copy(projectId = "project-b")))
    }

    @Test
    fun `result is rejected after clip source changes`() {
        assertFalse(backgroundApplyCanCommit(original, original.copy(uri = "content://media/replacement.mp4")))
    }

    @Test
    fun `replacement image must be an image clip in the current project`() {
        val clips = listOf(
            MediaClip("video", "Video", "content://project/video", 10_000L,
                trimEndMs = 10_000L, type = ClipType.VIDEO),
            MediaClip("photo", "Photo", "content://project/photo", 1L,
                trimEndMs = 1L, type = ClipType.IMAGE),
        )

        assertTrue(isExistingProjectImageSource(clips, "content://project/photo"))
        assertFalse(isExistingProjectImageSource(clips, "content://project/video"))
        assertFalse(isExistingProjectImageSource(clips, "content://unrelated/photo"))
        assertFalse(isExistingProjectImageSource(clips, null))
        assertFalse(isExistingProjectImageSource(clips, " "))
    }

    @Test
    fun `result is rejected after target clip is removed`() {
        assertFalse(backgroundApplyCanCommit(original, null))
    }

    @Test
    fun `result is rejected if original is restored while processing`() {
        val applied = original.copy(
            uri = "file:///bg_replace/processed.mp4",
            backgroundReplace = BackgroundReplaceSettings(
                mode = BackgroundReplaceMode.BLUR,
                sourceUri = original.uri
            )
        )
        assertFalse(backgroundApplyCanCommit(applied, original))
    }
}
