package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.fx.DownloadedEffectRepository
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.data.template.ShortsTemplateCatalog
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.ProjectFxSettings
import com.apexstudio.app.domain.model.ProjectLutSettings
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

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

    @Test
    fun `manual LUT override survives template reopen undo redo export and explicit reset`(): Unit = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "project-lut-${System.nanoTime()}")
        val files = File(root, "files").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
        }
        val projectId = "project-lut-${System.nanoTime()}"
        val repository = ProjectRepository(context)
        val template = ShortsTemplateCatalog.templates.first().asTransmissionTemplate()
        val manualFilterId = ShortsTemplateCatalog.templates.first { it.filterId != template.filterId }.filterId
        val store = ViewModelStore()

        fun openEditor(): EditorViewModel = EditorViewModel(context = context, projectId = projectId).also {
            store.put("project-lut-editor", it)
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

        suspend fun awaitLutState(
            editor: EditorViewModel,
            filterId: String?,
            intensity: Float,
            expectedOverride: ProjectLutSettings?
        ) {
            withTimeout(20_000L) {
                editor.state.first { state ->
                    val project = state.project
                    project?.id == projectId && project.manualLutOverride == expectedOverride &&
                        state.activeFilterId == filterId &&
                        kotlin.math.abs(state.filterIntensity - intensity) < 0.0001f
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
                    name = "LUT persistence",
                    durationMs = 10_000L,
                    lastTransmissionTemplateId = template.id,
                    lastTransitionType = template.transitionType,
                    lastTransitionDurationMs = template.transitionDurationMs
                )
            )

            var editor = openEditor()
            awaitTemplate(editor)
            var savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.applyManualLutFilter(manualFilterId)
            awaitSaved(editor, savedAt)
            awaitLutState(
                editor, manualFilterId, template.filterIntensity,
                ProjectLutSettings(manualFilterId, template.filterIntensity)
            )

            savedAt = editor.state.value.lastAutoSaveMs
            val undoCountBeforeSlider = editor.undoStack.size
            delay(2L)
            editor.setFilterIntensity(0.65f)
            editor.setFilterIntensity(0.42f)
            assertEquals(undoCountBeforeSlider, editor.undoStack.size)
            editor.commitManualLutSettings()
            assertEquals(undoCountBeforeSlider + 1, editor.undoStack.size)
            awaitSaved(editor, savedAt)
            awaitLutState(editor, manualFilterId, 0.42f, ProjectLutSettings(manualFilterId, 0.42f))

            val preset = com.apexstudio.app.data.filter.LutFilterEngine(context)
                .manifest.presetById(manualFilterId)
            assertNotNull(preset)
            val exportConfig = buildProjectExportConfig(
                editor.state.value,
                AudioStudioState(),
                com.apexstudio.app.domain.model.ExportSettings(
                    resolution = "1080p", frameRate = 30, aspectRatio = "16:9", bitrateMbps = 12
                ),
                preset
            )
            assertEquals(manualFilterId, exportConfig.filterPreset?.id)
            assertEquals(0.42f, exportConfig.filterIntensity, 0.0001f)

            savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.undo()
            awaitSaved(editor, savedAt)
            awaitLutState(
                editor, manualFilterId, template.filterIntensity,
                ProjectLutSettings(manualFilterId, template.filterIntensity)
            )

            savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.undo()
            awaitSaved(editor, savedAt)
            awaitLutState(editor, template.filterId, template.filterIntensity, null)

            savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.redo()
            awaitSaved(editor, savedAt)
            awaitLutState(
                editor, manualFilterId, template.filterIntensity,
                ProjectLutSettings(manualFilterId, template.filterIntensity)
            )

            savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.redo()
            awaitSaved(editor, savedAt)
            awaitLutState(editor, manualFilterId, 0.42f, ProjectLutSettings(manualFilterId, 0.42f))

            store.clear()
            editor = openEditor()
            withTimeout(20_000L) {
                editor.state.first { state ->
                    val project = state.project
                    project?.id == projectId &&
                        project.manualLutOverride == ProjectLutSettings(manualFilterId, 0.42f) &&
                        state.activeFilterId == manualFilterId && state.filterIntensity == 0.42f &&
                        state.lastTransitionType == template.transitionType
                }
            }

            editor.setLutContrast(1.45f)
            editor.setLutSaturation(1.3f)
            editor.setLutTemperature(6800f)
            editor.setLutTint(22f)
            editor.toggleLutCompareMode()
            savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.resetLutGrading()
            awaitSaved(editor, savedAt)
            val explicitClear = ProjectLutSettings(filterId = null, intensity = 1f)
            awaitLutState(editor, null, 1f, explicitClear)
            assertEquals(1f, editor.state.value.lutContrast, 0.0001f)
            assertEquals(1f, editor.state.value.lutSaturation, 0.0001f)
            assertEquals(5000f, editor.state.value.lutTemperature, 0.0001f)
            assertEquals(0f, editor.state.value.lutTint, 0.0001f)
            assertEquals(false, editor.state.value.lutCompareMode)

            savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.undo()
            awaitSaved(editor, savedAt)
            awaitLutState(editor, manualFilterId, 0.42f, ProjectLutSettings(manualFilterId, 0.42f))
            assertEquals(1.45f, editor.state.value.lutContrast, 0.0001f)
            assertEquals(1.3f, editor.state.value.lutSaturation, 0.0001f)
            assertEquals(6800f, editor.state.value.lutTemperature, 0.0001f)
            assertEquals(22f, editor.state.value.lutTint, 0.0001f)
            assertEquals(true, editor.state.value.lutCompareMode)

            savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.redo()
            awaitSaved(editor, savedAt)
            awaitLutState(editor, null, 1f, explicitClear)
            assertEquals(1f, editor.state.value.lutContrast, 0.0001f)
            assertEquals(1f, editor.state.value.lutSaturation, 0.0001f)
            assertEquals(5000f, editor.state.value.lutTemperature, 0.0001f)
            assertEquals(0f, editor.state.value.lutTint, 0.0001f)
            assertEquals(false, editor.state.value.lutCompareMode)
            assertEquals(explicitClear, withContext(Dispatchers.IO) { repository.findById(projectId)!!.manualLutOverride })

            store.clear()
            val reopened = openEditor()
            withTimeout(20_000L) {
                reopened.state.first { state ->
                    val project = state.project
                    project?.id == projectId && project.manualLutOverride == explicitClear &&
                        state.activeFilterId == null && state.filterIntensity == 1f &&
                        state.lastTransitionType == template.transitionType
                }
            }
            Unit
        } finally {
            store.clear()
            repository.deleteProject(projectId)
            withContext(Dispatchers.IO) { root.deleteRecursively() }
        }
    }

    @Test
    fun `installed downloaded FX override survives repository reopen reaches export and clear`() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "downloaded-fx-persistence-${System.nanoTime()}")
        val files = File(root, "files").apply { mkdirs() }
        val external = File(root, "external").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
            override fun getExternalFilesDir(type: String?): File =
                File(external, type ?: "").apply { mkdirs() }
        }
        val projectId = "downloaded-fx-${System.nanoTime()}"
        val packId = "offline-fx-${System.nanoTime()}"
        val repository = ProjectRepository(context)
        val template = ShortsTemplateCatalog.templates.first().asTransmissionTemplate()
        val store = ViewModelStore()
        val payload = downloadedFxPayload()
        val pack = PackManifestRepo.RemotePack(
            id = packId,
            name = "Offline FX persistence fixture",
            category = "effects",
            sizeBytes = payload.size.toLong(),
            url = "https://example.invalid/$packId/effects.json",
            sha256 = sha256(payload),
            entry = "effects.json",
            itemCount = 1,
            payloadType = DownloadedEffectRepository.PAYLOAD_TYPE,
            entrySha256 = sha256(payload)
        )
        val effectId = "dl_${packId}_offline_prism"

        fun openEditor(): EditorViewModel = EditorViewModel(context = context, projectId = projectId).also {
            store.put("downloaded-fx-editor", it)
        }

        suspend fun awaitLoaded(editor: EditorViewModel) {
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
            val packRoot = PackManager.packDir(context, packId).apply { mkdirs() }
            File(packRoot, pack.entry).writeBytes(payload)
            File(PackManager.packsRoot(context), "installed.json").writeText(
                JSONObject().put(
                    "packs",
                    JSONObject().put(
                        packId,
                        JSONObject().put("version", pack.version).put("sha256", pack.sha256)
                    )
                ).toString()
            )
            assertEquals(
                1,
                DownloadedEffectRepository.refreshInstalled(
                    context,
                    PackManifestRepo.PackManifestFile(packs = listOf(pack))
                )
            )
            assertEquals(effectId, DownloadedEffectRepository.effects.value.single().id)

            repository.saveProject(
                Project(
                    id = projectId,
                    name = "Downloaded FX persistence",
                    durationMs = 10_000L,
                    lastTransmissionTemplateId = template.id,
                    lastTransitionType = template.transitionType,
                    lastTransitionDurationMs = template.transitionDurationMs
                )
            )

            val firstOpen = openEditor()
            awaitLoaded(firstOpen)
            var savedAt = firstOpen.state.value.lastAutoSaveMs
            delay(2L)
            firstOpen.selectFx(effectId, intensity = 0.37f)
            withTimeout(20_000L) {
                firstOpen.state.first { it.activeFxId == effectId && it.fxIntensity == 0.37f }
            }
            awaitSaved(firstOpen, savedAt)
            savedAt = firstOpen.state.value.lastAutoSaveMs
            delay(2L)
            firstOpen.setFxSpeed(1.8f)
            firstOpen.flushProject()
            awaitSaved(firstOpen, savedAt)

            val stored = withContext(Dispatchers.IO) { repository.findById(projectId)!! }
            assertEquals(ProjectFxSettings(effectId, 0.37f, 1.8f), stored.manualFxOverride)

            store.clear()
            val reopened = openEditor()
            awaitLoaded(reopened)
            withTimeout(20_000L) {
                reopened.state.first { it.activeFxId == effectId }
            }
            assertEquals(effectId, reopened.state.value.project?.manualFxOverride?.effectId)
            assertEquals(0.37f, reopened.state.value.fxIntensity, 0.0001f)
            assertEquals(1.8f, reopened.state.value.fxSpeed, 0.0001f)

            val exportConfig = buildProjectExportConfig(
                reopened.state.value,
                AudioStudioState(),
                com.apexstudio.app.domain.model.ExportSettings(
                    resolution = "1080p", frameRate = 30, aspectRatio = "16:9", bitrateMbps = 12
                )
            )
            assertEquals(effectId, exportConfig.downloadedFxEffectId)
            assertNull(exportConfig.downloadedFxEffect)
            assertEquals(0.37f, exportConfig.fxIntensity, 0.0001f)
            assertEquals(1.8f, exportConfig.fxSpeed, 0.0001f)

            savedAt = reopened.state.value.lastAutoSaveMs
            delay(2L)
            reopened.setActiveFx(null)
            awaitSaved(reopened, savedAt)
            store.clear()

            val cleared = openEditor()
            withTimeout(20_000L) {
                cleared.state.first { state ->
                    val project = state.project
                    project?.id == projectId && project.manualFxOverride != null &&
                        project.manualFxOverride.effectId == null && state.activeFxId == null &&
                        state.activeFilterId == template.filterId &&
                        state.lastTransitionType == template.transitionType
                }
            }
            assertNull(cleared.state.value.activeFxId)
            assertNotNull(cleared.state.value.project?.manualFxOverride)
            assertNull(cleared.state.value.project?.manualFxOverride?.effectId)
        } finally {
            store.clear()
            repository.deleteProject(projectId)
            DownloadedEffectRepository.refreshInstalled(context, PackManifestRepo.PackManifestFile())
            withContext(Dispatchers.IO) { root.deleteRecursively() }
        }
    }

    private fun downloadedFxPayload(): ByteArray = JSONObject()
        .put("schemaVersion", 1)
        .put("attribution", "Project persistence regression fixture")
        .put(
            "effects",
            JSONArray().put(
                JSONObject()
                    .put("id", "offline_prism")
                    .put("name", "Offline Prism")
                    .put("category", "Regression")
                    .put("family", "PRISM")
                    .put("a", 1.5)
                    .put("b", 0.12)
                    .put("c", 0.38)
                    .put("tint", JSONArray().put(0.2).put(0.7).put(0.9))
                    .put("animated", true)
                    .put("defaultSpeed", 1.25)
            )
        )
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
