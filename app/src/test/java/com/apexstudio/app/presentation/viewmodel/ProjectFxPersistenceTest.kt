package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.data.template.ShortsTemplateCatalog
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.ProjectFxSettings
import com.apexstudio.app.presentation.state.AudioStudioState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectFxPersistenceTest {
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `manual FX override survives template reopen reaches export and explicit clear stays cleared`() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "project-fx-${System.nanoTime()}")
        val files = File(root, "files").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
        }
        val projectId = "project-fx-${System.nanoTime()}"
        val repository = ProjectRepository(context)
        val template = ShortsTemplateCatalog.templates.first().asTransmissionTemplate()
        val store = ViewModelStore()

        fun openEditor(): EditorViewModel = EditorViewModel(context = context, projectId = projectId).also {
            store.put("project-fx-editor", it)
        }

        suspend fun awaitTemplate(editor: EditorViewModel) {
            withTimeout(20_000L) {
                editor.state.first {
                    it.project?.id == projectId &&
                        it.activeFilterId == template.filterId &&
                        it.lastTransitionType == template.transitionType
                }
            }
        }

        suspend fun awaitSaved(editor: EditorViewModel, previous: Long) {
            withTimeout(30_000L) {
                editor.state.first { it.lastAutoSaveMs > previous }
            }
        }

        try {
            repository.saveProject(
                Project(
                    id = projectId,
                    name = "FX persistence",
                    durationMs = 10_000L,
                    lastTransmissionTemplateId = template.id,
                    lastTransitionType = template.transitionType,
                    lastTransitionDurationMs = template.transitionDurationMs
                )
            )

            val firstOpen = openEditor()
            awaitTemplate(firstOpen)
            assertEquals(template.fxPresetId, firstOpen.state.value.activeFxId)

            var savedAt = firstOpen.state.value.lastAutoSaveMs
            delay(2L)
            firstOpen.setActiveFx(FxPreset.HUE_CYCLE.id)
            awaitSaved(firstOpen, savedAt)
            savedAt = firstOpen.state.value.lastAutoSaveMs
            delay(2L)
            firstOpen.setFxIntensity(0.42f)
            firstOpen.setFxSpeed(1.75f)
            firstOpen.flushProject()
            awaitSaved(firstOpen, savedAt)

            val stored = withContext(Dispatchers.IO) { repository.findById(projectId)!! }
            assertEquals(
                ProjectFxSettings(FxPreset.HUE_CYCLE.id, 0.42f, 1.75f),
                stored.manualFxOverride
            )
            assertEquals(template.id, stored.lastTransmissionTemplateId)
            assertEquals(template.transitionType, stored.lastTransitionType)
            assertEquals(template.transitionDurationMs, stored.lastTransitionDurationMs)

            store.clear()
            val reopened = openEditor()
            awaitTemplate(reopened)
            withTimeout(20_000L) {
                reopened.state.first { it.activeFxId == FxPreset.HUE_CYCLE.id }
            }
            assertEquals(template.filterId, reopened.state.value.activeFilterId)
            assertEquals(template.transitionType, reopened.state.value.lastTransitionType)
            assertEquals(0.42f, reopened.state.value.fxIntensity, 0.0001f)
            assertEquals(1.75f, reopened.state.value.fxSpeed, 0.0001f)

            val exportConfig = buildProjectExportConfig(
                reopened.state.value,
                AudioStudioState(),
                com.apexstudio.app.domain.model.ExportSettings(
                    resolution = "1080p", frameRate = 30, aspectRatio = "16:9", bitrateMbps = 12
                )
            )
            assertEquals(FxPreset.HUE_CYCLE, exportConfig.fxPreset)
            assertEquals(0.42f, exportConfig.fxIntensity, 0.0001f)
            assertEquals(1.75f, exportConfig.fxSpeed, 0.0001f)

            savedAt = reopened.state.value.lastAutoSaveMs
            delay(2L)
            reopened.setActiveFx(null)
            awaitSaved(reopened, savedAt)
            store.clear()

            val cleared = openEditor()
            withTimeout(20_000L) {
                cleared.state.first { state ->
                    val loadedProject = state.project
                    loadedProject != null && loadedProject.id == projectId &&
                        loadedProject.manualFxOverride?.effectId == null &&
                        state.activeFxId == null &&
                        state.activeFilterId == template.filterId &&
                        state.lastTransitionType == template.transitionType
                }
            }
            assertNull(cleared.state.value.activeFxId)
            assertEquals(template.filterId, cleared.state.value.activeFilterId)
            assertEquals(template.transitionType, cleared.state.value.lastTransitionType)
        } finally {
            store.clear()
            repository.deleteProject(projectId)
            withContext(Dispatchers.IO) { root.deleteRecursively() }
        }
    }
}
