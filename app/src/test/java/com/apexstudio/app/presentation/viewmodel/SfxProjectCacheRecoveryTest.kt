package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.audio.sfx.SfxWavCache
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.Project
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SfxProjectCacheRecoveryTest {
    @Test
    fun `reopening a project regenerates its cached WAV from the persisted effect recipe`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val effectId = "sparkle"
            val durationMs = 800L
            val intensity = 0.65f
            val cached = SfxWavCache.getOrCreate(context, effectId, durationMs, intensity)
            val originalHash = cached.readBytes().contentHashCode()
            val track = AudioTrack(
                id = "saved-sfx",
                name = "Sparkle",
                uri = cached.absolutePath,
                kind = AudioTrack.Kind.SFX,
                sourceDurationMs = durationMs,
                trimEndMs = durationMs,
                sfxEffectId = effectId,
                sfxIntensity = intensity
            )
            val project = Project(
                id = "sfx-cache-recovery-${System.nanoTime()}",
                name = "SFX cache recovery",
                durationMs = durationMs,
                audioTracks = listOf(track)
            )
            ProjectRepository(context).saveProject(project)

            assertTrue(File(context.cacheDir, "sfx").deleteRecursively())
            val editor = EditorViewModel(context = context, projectId = project.id)
            val loaded = withTimeout(10_000L) {
                editor.state.first { it.project?.id == project.id }.project!!
            }

            val restored = loaded.audioTracks.single()
            assertEquals(track.uri, restored.uri)
            assertEquals(effectId, restored.sfxEffectId)
            assertEquals(intensity, restored.sfxIntensity, 0.001f)
            assertTrue(File(restored.uri).isFile)
            assertEquals(originalHash, File(restored.uri).readBytes().contentHashCode())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
