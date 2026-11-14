package com.apexstudio.app.data.aireport

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.viewmodel.AiContentReportController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiContentReportTest {
    private fun project() = Project(
        id = "project-current", name = "Current", durationMs = 12_000L,
        clips = listOf(MediaClip(
            id = "clip-current", name = "Title", uri = "content://local/item", durationMs = 12_000L,
            trimEndMs = 12_000L, type = ClipType.VIDEO,
            textOverlays = listOf(TextOverlay(id = "caption-current", text = "edited caption"))
        ))
    )

    @Test fun targetUsesOnlyCurrentProjectAndSelectedExistingClipOrOverlay() {
        val project = project()
        val target = resolveAiContentReportTarget("project-current", project, "clip-current", "caption-current")
        assertEquals(AiContentReportTarget("project-current", "clip-current", "VIDEO", "caption-current"), target)
        assertNull(resolveAiContentReportTarget("different-project", project, "clip-current", null))
        assertNull(resolveAiContentReportTarget("project-current", project, "missing-clip", null))
        assertNull(resolveAiContentReportTarget("project-current", project, null, null))
        assertEquals(null, resolveAiContentReportTarget("project-current", project, "clip-current", "stale-overlay")?.textOverlayId)
    }

    @Test fun currentTargetPolicyRejectsSwitchedProjectsRemovedClipsAndNonVideoLayers() {
        val currentProject = project()
        val target = resolveAiContentReportTarget(
            "project-current", currentProject, "clip-current", "caption-current"
        )!!

        assertTrue(isCurrentAiContentReportTarget("project-current", currentProject, target))
        assertFalse(isCurrentAiContentReportTarget("other-project", currentProject, target))
        assertFalse(isCurrentAiContentReportTarget("project-current", currentProject, target.copy(clipId = "removed")))
        assertFalse(isCurrentAiContentReportTarget("project-current", currentProject, target.copy(clipType = "IMAGE")))
        assertFalse(isCurrentAiContentReportTarget("project-current", currentProject, target.copy(textOverlayId = "removed")))
        assertFalse(isCurrentAiContentReportTarget(
            "project-current", currentProject.copy(clips = listOf(currentProject.clips.single().copy(trackIndex = 1))), target
        ))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun submissionCallbackPersistsReferenceAndReasonAcrossStoreInstances() = runTest {
        val directory = Files.createTempDirectory("ai-report-test").toFile()
        try {
            val store = FileAiContentReportStore(directory)
            val dispatcher = StandardTestDispatcher(testScheduler)
            val controller = AiContentReportController(store, this, dispatcher)
            val target = resolveAiContentReportTarget("project-current", project(), "clip-current", "caption-current")!!

            controller.submit(target, AiContentReportReason.HATEFUL) {
                isCurrentAiContentReportTarget("project-current", project(), target)
            }
            assertTrue(controller.state.value.isSaving)
            advanceUntilIdle()

            assertTrue(controller.state.value.saved)
            assertTrue(controller.state.value.message!!.contains("not sent"))
            val persisted = FileAiContentReportStore(directory).readAll()
            assertEquals(1, persisted.size)
            assertEquals(target, persisted.single().target)
            assertEquals(AiContentReportReason.HATEFUL, persisted.single().reason)
            val journal = directory.resolve(FileAiContentReportStore.FILE_NAME).readText()
            listOf("edited caption", "content://local/item", "Title").forEach {
                assertFalse("Journal must not copy user content: $it", journal.contains(it))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun targetThatBecomesStaleBeforeAsyncAppendIsNotPersisted() = runTest {
        var targetCurrent = true
        var persisted: AiContentReport? = null
        val store = object : AiContentReportStore {
            override fun append(report: AiContentReport) { persisted = report }
        }
        val controller = AiContentReportController(store, this, StandardTestDispatcher(testScheduler))
        val target = resolveAiContentReportTarget(
            "project-current", project(), "clip-current", "caption-current"
        )!!

        controller.submit(target, AiContentReportReason.OTHER) { targetCurrent }
        assertTrue(controller.state.value.isSaving)
        targetCurrent = false
        advanceUntilIdle()

        assertNull(persisted)
        assertFalse(controller.state.value.saved)
        assertTrue(controller.state.value.message!!.contains("no longer in the current project"))
    }

    @Test fun concurrentEditorStoresDoNotLoseReports() {
        val directory = Files.createTempDirectory("ai-report-concurrency").toFile()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val target = AiContentReportTarget("project-current", "clip-current", "VIDEO")
            val stores = List(2) { FileAiContentReportStore(directory) }
            val reports = List(20) { AiContentReport(target = target, reason = AiContentReportReason.OTHER) }
            val futures = reports.mapIndexed { index, report ->
                pool.submit { stores[index % stores.size].append(report) }
            }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            val saved = FileAiContentReportStore(directory).readAll()
            assertEquals(reports.map { it.id }.toSet(), saved.map { it.id }.toSet())
            assertEquals(reports.size, saved.size)
            assertFalse(directory.resolve("${FileAiContentReportStore.FILE_NAME}.tmp").exists())
        } finally {
            pool.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun invalidReferenceAndStorageFailureGiveVisibleFailureWithoutSuccess() = runTest {
        val controller = AiContentReportController(object : AiContentReportStore {
            override fun append(report: AiContentReport) { error("disk unavailable") }
        }, this, StandardTestDispatcher(testScheduler))
        controller.submit(null, AiContentReportReason.OTHER) { false }
        assertFalse(controller.state.value.isSaving)
        assertFalse(controller.state.value.saved)
        assertTrue(controller.state.value.message!!.contains("Select a valid"))

        val target = AiContentReportTarget("project-current", "clip-current", "VIDEO")
        controller.submit(target, AiContentReportReason.OTHER) { true }
        advanceUntilIdle()
        assertFalse(controller.state.value.saved)
        assertTrue(controller.state.value.message!!.contains("Could not save"))
    }
}
