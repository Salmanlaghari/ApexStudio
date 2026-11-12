package com.apexstudio.app.data.fx

import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Catalog and runtime-wiring audit; intentionally does not create a GL context
 * or assert rendered output.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedFxCatalogAuditTest {
    @OptIn(UnstableApi::class)
    @Test
    fun `published 30 effect pack has unique verified profiles and video preview export shader support`() {
        val repoRoot = findRepoRoot()
        val catalogFile = File(repoRoot, "packs/manifest.json")
        val catalog = Json { ignoreUnknownKeys = true }
            .decodeFromString<PackManifestRepo.PackManifestFile>(catalogFile.readText())
        val pack = catalog.packs.single { it.id == DownloadedEffectRepository.PACK_ID }

        assertEquals("effects", pack.category)
        assertEquals(DownloadedEffectRepository.PAYLOAD_TYPE, pack.payloadType)
        assertEquals("available", pack.status)
        assertEquals("30 original downloadable profiles must remain published", 30, pack.itemCount)
        assertTrue("The complete downloadable effect file must stay at or below 1 MiB", pack.sizeBytes in 1L..(1024L * 1024L))

        val payloadFile = File(repoRoot, "packs/effects/${pack.entry}")
        assertTrue("Missing published effect payload: ${payloadFile.path}", payloadFile.isFile)
        val payloadBytes = payloadFile.readBytes()
        assertEquals("Manifest size must describe the real downloadable file", pack.sizeBytes, payloadBytes.size.toLong())
        val payloadHash = sha256(payloadBytes)
        assertEquals("Pack download SHA must match the source payload", pack.sha256.lowercase(), payloadHash)
        assertEquals("Installed entry SHA must match the parsed payload", pack.entrySha256.lowercase(), payloadHash)

        val emulatorFixture = File(repoRoot, "app/src/androidTest/assets/fx_profiles.json")
        assertTrue("Missing offline emulator shader fixture: ${emulatorFixture.path}", emulatorFixture.isFile)
        assertArrayEquals(
            "Emulator shader fixture must be the exact published FX payload",
            payloadBytes,
            emulatorFixture.readBytes()
        )

        val effects = DownloadedEffectRepository.loadPackEffects(payloadFile.parentFile!!, pack)
        assertEquals("All 30 published profiles must pass the runtime validator", pack.itemCount, effects.size)
        assertEquals("Every downloaded profile ID must be unique", effects.size, effects.map { it.id }.toSet().size)
        assertTrue(effects.all { it.id.startsWith("dl_${pack.id}_") })
        assertEquals(
            setOf("PRISM", "SPLIT_TONE", "RIPPLE", "MOSAIC", "CRT", "HALATION", "INK", "MIRROR", "GLITCH", "STREAK"),
            effects.map { it.family }.toSet()
        )

        val shaderSources = mutableSetOf<String>()
        for (effect in effects) {
            val shader = ModernFxShaders.shaderFor(effect)
            assertNotNull("${effect.id} must resolve to app-owned shader code", shader)
            val source = shader!!
            assertTrue("${effect.id} must contain a fragment entry point", source.contains("void main()"))
            assertTrue("${effect.id} must sample the source video frame", source.contains("texture2D(uTexSampler"))
            assertTrue("${effect.id} must write the effected pixels", source.contains("gl_FragColor"))
            assertTrue("${effect.id} must honor the FX strength control", source.contains("uIntensity"))
            if (effect.isAnimated) {
                assertTrue("${effect.id} must honor playback time and speed", source.contains("uTime * uSpeed"))
            }
            shaderSources += source

            // The live video preview and both export routes use FxGlEffect for downloaded profiles.
            // Still-photo previews use the separate family-aware Compose overlay.
            val previewRenderer = FxGlEffect(effect, intensity = 0.73f, speed = effect.defaultSpeed)
            assertNotNull(previewRenderer)
            val exportConfig = ExportEngine.ExportConfig(
                downloadedFxEffect = effect,
                fxIntensity = 0.73f,
                fxSpeed = effect.defaultSpeed
            )
            assertSame(effect, exportConfig.downloadedFxEffect)
            val exportRenderer = FxGlEffect(
                requireNotNull(exportConfig.downloadedFxEffect),
                intensity = exportConfig.fxIntensity,
                speed = exportConfig.fxSpeed
            )
            assertNotNull(exportRenderer)
        }
        assertEquals("The 30 downloadable profiles must not collapse to a smaller set of shader outputs", effects.size, shaderSources.size)
    }

    private fun findRepoRoot(): File {
        var current: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(8) {
            val candidate = current ?: return@repeat
            if (File(candidate, "packs/manifest.json").isFile) return candidate
            current = candidate.parentFile
        }
        error("Could not find repository root from ${System.getProperty("user.dir")}")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
