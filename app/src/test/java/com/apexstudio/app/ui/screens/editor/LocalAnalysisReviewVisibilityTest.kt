package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.FaceTrackSourceSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAnalysisReviewVisibilityTest {
    @Test fun resultsAreReviewableOnlyWhileTheCapturedSourceAndEditSettingsMatch() {
        val clip = MediaClip("clip", "Video", "file:///video.mp4", 3_000L,
            trimStartMs = 500L, trimEndMs = 2_000L)
        val project = Project("project", "Project", 3_000L, clips = listOf(clip))
        val snapshot = FaceTrackSourceSnapshot.from(project.id, clip)

        assertTrue(isLocalReviewSourceCurrent(snapshot, project))
        assertFalse(isLocalReviewSourceCurrent(null, project))
        assertFalse(isLocalReviewSourceCurrent(snapshot, null))
        assertFalse(isLocalReviewSourceCurrent(snapshot, project.copy(id = "another-project")))
        assertFalse(isLocalReviewSourceCurrent(snapshot, project.copy(clips = emptyList())))
        assertFalse(isLocalReviewSourceCurrent(snapshot,
            project.copy(clips = listOf(clip.copy(uri = "file:///replacement.mp4")))))
        assertFalse(isLocalReviewSourceCurrent(snapshot,
            project.copy(clips = listOf(clip.copy(trimEndMs = 1_900L)))))
    }
}
