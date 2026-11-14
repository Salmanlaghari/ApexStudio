package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.aireport.AiContentReportReason
import com.apexstudio.app.data.aireport.AiContentReportTarget
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorViewModelAiContentReportTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun submitRejectsReportTargetFromAnotherProjectBeforePersistence() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val clip = MediaClip(
                id = "current-video",
                name = "Current video",
                uri = "file:///current.mp4",
                durationMs = 5_000L,
                trimEndMs = 5_000L,
                type = ClipType.VIDEO
            )
            val viewModel = EditorViewModel(projectId = "current-project")
            viewModel._state.value = EditorState(
                project = Project("current-project", "Current", 5_000L, clips = listOf(clip))
            )

            viewModel.submitAiContentReport(
                AiContentReportTarget("other-project", clip.id, "VIDEO"),
                AiContentReportReason.OTHER
            )

            val result = viewModel.aiContentReportState.value
            assertFalse(result.isSaving)
            assertFalse(result.saved)
            assertTrue(result.message!!.contains("no longer in the current project"))
        } finally {
            Dispatchers.resetMain()
        }
    }
}
