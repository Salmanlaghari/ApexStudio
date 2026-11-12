package com.apexstudio.app.data.fx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedEffectRepositoryTest {
    private val families = listOf(
        Triple("PRISM", floatArrayOf(1.5f, 0.12f, 0.38f), true),
        Triple("SPLIT_TONE", floatArrayOf(0.24f, 0.38f, 0.68f), false),
        Triple("RIPPLE", floatArrayOf(2f, 0.014f, 3f), true),
        Triple("MOSAIC", floatArrayOf(5f, 7f, 0.02f), false),
        Triple("CRT", floatArrayOf(120f, 0.20f, 0.12f), true),
        Triple("HALATION", floatArrayOf(0.30f, 7f, 0.62f), false),
        Triple("INK", floatArrayOf(0.18f, 0.52f, 0.15f), false),
        Triple("MIRROR", floatArrayOf(4f, 0f, 0.72f), true),
        Triple("GLITCH", floatArrayOf(7f, 0.006f, 0.18f), true),
        Triple("STREAK", floatArrayOf(0f, 0.35f, 0.64f), true)
    )

    @Test
    fun `pinned numeric pack validates all supported families and builds real shaders`() {
        val bytes = payload()
        val root = java.nio.file.Files.createTempDirectory("fx-profile-test-").toFile()
        try {
            File(root, "effects.json").writeBytes(bytes)
            val effects = DownloadedEffectRepository.loadPackEffects(root, pack(bytes))

            assertEquals(families.size, effects.size)
            for (effect in effects) {
                assertTrue("${effect.family} should be supported", ModernFxShaders.supportsFamily(effect.family))
                val shader = ModernFxShaders.shaderFor(effect)
                assertNotNull("${effect.family} should create a shader", shader)
                assertTrue("${effect.family} shader should sample the source", shader!!.contains("texture2D(uTexSampler"))
                assertTrue("${effect.family} shader should blend by intensity", shader.contains("uIntensity"))
                if (effect.isAnimated) {
                    assertTrue("${effect.family} animated shader should use time", shader.contains("uTime * uSpeed"))
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `profile lookup fails closed after installed entry is tampered`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = payload()
        val pack = pack(bytes).copy(id = "integrity-test-effects")
        val root = PackManager.packDir(context, pack.id)
        val registry = File(PackManager.packsRoot(context), "installed.json")
        val previousRegistry = registry.takeIf { it.isFile }?.readText()
        try {
            root.deleteRecursively()
            root.mkdirs()
            File(root, pack.entry).writeBytes(bytes)
            registry.parentFile?.mkdirs()
            registry.writeText(JSONObject().put(
                "packs",
                JSONObject().put(pack.id, JSONObject().put("version", pack.version).put("sha256", pack.sha256))
            ).toString())

            val catalog = PackManifestRepo.PackManifestFile(packs = listOf(pack))
            assertEquals(families.size, DownloadedEffectRepository.refreshInstalled(context, catalog))
            val effectId = "dl_${pack.id}_sample_prism"
            assertNotNull(DownloadedEffectRepository.find(effectId))

            val tampered = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            File(root, pack.entry).writeBytes(tampered)
            assertNull("Lookup must recheck the installed entry digest", DownloadedEffectRepository.find(effectId))
            assertTrue(DownloadedEffectRepository.effects.value.isEmpty())
        } finally {
            root.deleteRecursively()
            if (previousRegistry == null) registry.delete() else registry.writeText(previousRegistry)
            DownloadedEffectRepository.refreshInstalled(context, PackManifestRepo.PackManifestFile())
        }
    }

    @Test
    fun `split tone profile with unordered smoothstep edges is rejected`() {
        val document = JSONObject(payload().toString(Charsets.UTF_8))
        val items = document.getJSONArray("effects")
        val splitTone = (0 until items.length()).map { items.getJSONObject(it) }
            .first { it.optString("family") == "SPLIT_TONE" }
        splitTone.put("a", 0.8).put("b", 0.5)
        val bytes = document.toString().toByteArray(Charsets.UTF_8)
        val root = java.nio.file.Files.createTempDirectory("fx-profile-split-tone-").toFile()
        try {
            File(root, "effects.json").writeBytes(bytes)
            assertTrue(DownloadedEffectRepository.loadPackEffects(root, pack(bytes)).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `tampered payload and traversal entry never load as effects`() {
        val bytes = payload()
        val root = java.nio.file.Files.createTempDirectory("fx-profile-tamper-").toFile()
        try {
            val entry = File(root, "effects.json")
            entry.writeBytes(bytes)
            val validPack = pack(bytes)
            val tampered = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            entry.writeBytes(tampered)
            assertTrue(DownloadedEffectRepository.loadPackEffects(root, validPack).isEmpty())

            File(root.parentFile, "outside-effects.json").writeBytes(bytes)
            val traversal = validPack.copy(entry = "../outside-effects.json")
            assertTrue(DownloadedEffectRepository.loadPackEffects(root, traversal).isEmpty())
            assertFalse("Traversal target should not be accepted", root.canonicalFile
                .resolve("../outside-effects.json").canonicalPath.startsWith(root.canonicalPath + File.separator))
            File(root.parentFile, "outside-effects.json").delete()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun pack(bytes: ByteArray) = PackManifestRepo.RemotePack(
        id = "test-effects",
        name = "FX test pack",
        category = "effects",
        sizeBytes = bytes.size.toLong(),
        url = "https://example.invalid/effects.json",
        sha256 = sha256(bytes),
        entry = "effects.json",
        itemCount = families.size,
        payloadType = DownloadedEffectRepository.PAYLOAD_TYPE,
        entrySha256 = sha256(bytes)
    )

    private fun payload(): ByteArray {
        val items = JSONArray()
        for ((index, profile) in families.withIndex()) {
            val (family, values, animated) = profile
            items.put(JSONObject()
                .put("id", "sample_${family.lowercase()}")
                .put("name", "$family Sample")
                .put("category", "Creator Pack")
                .put("family", family)
                .put("a", values[0].toDouble())
                .put("b", values[1].toDouble())
                .put("c", values[2].toDouble())
                .put("tint", JSONArray().put(0.2 + index * 0.01).put(0.7).put(0.9))
                .put("animated", animated)
                .put("defaultSpeed", if (animated) 1.25 else 1.0))
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("attribution", "Original repository test profiles")
            .put("effects", items)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
