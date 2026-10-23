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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun submissionCallbackPersistsReferenceAndReasonAcrossStoreInstances() = runTest {
        val directory = Files.createTempDirectory("ai-report-test").toFile()
        try {
            val store = FileAiContentReportStore(directory)
            val dispatcher = StandardTestDispatcher(testScheduler)
            val controller = AiContentReportController(store, this, dispatcher)
            val target = resolveAiContentReportTarget("project-current", project(), "clip-current", "caption-current")!!

            controller.submit(target, AiContentReportReason.HATEFUL)
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
        controller.submit(null, AiContentReportReason.OTHER)
        assertFalse(controller.state.value.isSaving)
        assertFalse(controller.state.value.saved)
        assertTrue(controller.state.value.message!!.contains("Select a valid"))

        val target = AiContentReportTarget("project-current", "clip-current", "VIDEO")
        controller.submit(target, AiContentReportReason.OTHER)
        advanceUntilIdle()
        assertFalse(controller.state.value.saved)
        assertTrue(controller.state.value.message!!.contains("Could not save"))
    }
}
