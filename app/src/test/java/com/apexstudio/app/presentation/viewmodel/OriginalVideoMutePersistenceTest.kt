package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class OriginalVideoMutePersistenceTest {
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `original video mute survives project save and editor reopen`() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "original-mute-${System.nanoTime()}")
        val files = File(root, "files").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
        }
        val projectId = "original-mute-${System.nanoTime()}"
        val repository = ProjectRepository(context)
        val store = ViewModelStore()

        fun openEditor(): EditorViewModel = EditorViewModel(context = context, projectId = projectId).also {
            store.put("original-mute-editor", it)
        }

        try {
            repository.saveProject(Project(id = projectId, name = "Mute persistence", durationMs = 1_000L))
            val editor = openEditor()
            withTimeout(20_000L) {
                editor.state.first { it.project?.id == projectId }
            }
            assertFalse(editor.state.value.project!!.originalVideoMuted)

            val savedAt = editor.state.value.lastAutoSaveMs
            delay(2L)
            editor.setMuteOriginalVideo(true)
            withTimeout(30_000L) {
                editor.state.first { it.lastAutoSaveMs > savedAt }
            }
            assertTrue(editor.state.value.project!!.originalVideoMuted)
            assertTrue(editor.audio.value.isMuted)

            val stored = withContext(Dispatchers.IO) { ProjectRepository(context).findById(projectId)!! }
            assertTrue(stored.originalVideoMuted)

            store.clear()
            val reopened = openEditor()
            withTimeout(20_000L) {
                reopened.state.first { it.project?.id == projectId && it.project.originalVideoMuted }
            }
            withTimeout(20_000L) {
                reopened.audio.first { it.isMuted }
            }

            val legacy = repository.decode(
                """[{"id":"legacy-mute","name":"Legacy","durationMs":1000}]"""
            ).single()
            assertFalse(legacy.originalVideoMuted)
        } finally {
            store.clear()
            repository.deleteProject(projectId)
            withContext(Dispatchers.IO) { root.deleteRecursively() }
        }
    }
}
