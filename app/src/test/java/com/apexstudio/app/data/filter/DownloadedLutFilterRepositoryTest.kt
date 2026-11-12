package com.apexstudio.app.data.filter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedLutFilterRepositoryTest {
    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }
    private val packId = "test-lut-pack"

    @Before
    fun setUp() {
        FilterManifest.resetForTesting()
        PackManager.packDir(context, packId).deleteRecursively()
        removeInstalled(packId)
    }

    @After
    fun tearDown() {
        PackManager.packDir(context, packId).deleteRecursively()
        removeInstalled(packId)
        FilterManifest.resetForTesting()
    }

    @Test
    fun `verified installed LUT registers and is reverified each time it loads`() = runBlocking {
        val cube = validCube()
        val catalog = installPack(cube, "warm-shift.cube")

        assertEquals(1, DownloadedLutFilterRepository.refreshInstalled(context, catalog))
        val preset = FilterManifest.presetById("dl_${packId}_warm-shift")
        assertNotNull(preset)
        assertEquals("downloaded-lut://$packId/warm-shift.cube", preset!!.asset)
        assertEquals(cube.size.toLong(), preset.assetSizeBytes)

        val values = LutFilterEngine(context).loadLut(preset)
        assertNotNull(values)
        assertEquals(2 * 2 * 2 * 3, values!!.size)

        val installedFile = File(PackManager.packDir(context, packId), "warm-shift.cube")
        val tampered = cube.copyOf()
        tampered[0] = 'X'.code.toByte()
        installedFile.writeBytes(tampered)
        assertNull("A cached catalog entry must not bypass the current file hash", LutFilterEngine(context).loadLut(preset))
    }

    @Test
    fun `refresh removes filters for a pack no longer installed`() = runBlocking {
        val catalog = installPack(validCube(), "warm-shift.cube")
        assertEquals(1, DownloadedLutFilterRepository.refreshInstalled(context, catalog))
        assertNotNull(FilterManifest.presetById("dl_${packId}_warm-shift"))

        removeInstalled(packId)
        assertEquals(0, DownloadedLutFilterRepository.refreshInstalled(context, catalog))
        assertNull(FilterManifest.presetById("dl_${packId}_warm-shift"))
    }

    @Test
    fun `manifest traversal entry is rejected before it can become a preset`() = runBlocking {
        val catalog = installPack(validCube(), "../outside.cube")
        assertEquals(0, DownloadedLutFilterRepository.refreshInstalled(context, catalog))
        assertTrue(FilterManifest.downloadedPresets().isEmpty())
    }

    @Test
    fun `streaming hash matches digest and rejects wrong or oversized sizes gracefully`() {
        // PR #231 fix-forward (Kilo CRITICAL 2): verification streams the
        // file instead of readBytes(); the hash must match the classic
        // whole-file digest, and size problems must fail as null, not crash.
        val cube = validCube()
        val file = File(PackManager.packDir(context, packId).apply { mkdirs() }, "streaming.cube")
        file.writeBytes(cube)

        assertEquals(
            sha256(cube),
            DownloadedLutFilterRepository.sha256StreamingForTesting(file, cube.size.toLong())
        )
        // Wrong expected size -> graceful null.
        assertNull(DownloadedLutFilterRepository.sha256StreamingForTesting(file, cube.size.toLong() + 1))
        // Expected size beyond the sane cap -> graceful null, no OOM/crash.
        assertNull(
            DownloadedLutFilterRepository.sha256StreamingForTesting(
                file,
                DownloadedLutFilterRepository.MAX_CUBE_BYTES_FOR_TESTING + 1
            )
        )
        // Missing file -> graceful null.
        assertNull(
            DownloadedLutFilterRepository.sha256StreamingForTesting(File(file.parentFile, "nope.cube"), 10L)
        )
    }

    @Test
    fun `preset claiming a size beyond the cap is rejected without crashing`() = runBlocking {
        val cube = validCube()
        val catalog = installPack(cube, "warm-shift.cube")
        assertEquals(1, DownloadedLutFilterRepository.refreshInstalled(context, catalog))
        val preset = FilterManifest.presetById("dl_${packId}_warm-shift")!!
        val oversized = preset.copy(
            assetSizeBytes = DownloadedLutFilterRepository.MAX_CUBE_BYTES_FOR_TESTING + 1
        )
        assertNull(DownloadedLutFilterRepository.loadVerifiedLut(context, oversized))
        val missingMetadata = preset.copy(assetSha256 = null, assetSizeBytes = null)
        assertNull(DownloadedLutFilterRepository.loadVerifiedLut(context, missingMetadata))
    }

    @Test
    fun `downloaded preset without pinned metadata is refused by the bitmap cache`() = runBlocking {
        // PR #231 fix-forward (Kilo WARNING 5): no degenerate fingerprint,
        // no cache load, for a downloaded preset lacking sha256/size.
        val preset = FilterPreset(
            id = "dl_${packId}_no-meta", name = "No Meta", category = "warm",
            asset = "downloaded-lut://$packId/warm-shift.cube"
        )
        assertNull(LutBitmapCache.getOrLoad(context, preset))
    }

    private fun installPack(cube: ByteArray, path: String): PackManifestRepo.PackManifestFile {
        val directory = PackManager.packDir(context, packId).apply { mkdirs() }
        val cubeFile = File(directory, "warm-shift.cube")
        cubeFile.writeBytes(cube)
        val entry = JSONObject()
            .put("schemaVersion", 1)
            .put("attribution", "Original test LUT")
            .put("filters", JSONArray().put(
                JSONObject()
                    .put("id", "warm-shift")
                    .put("name", "Warm Shift")
                    .put("category", "warm")
                    .put("file", path)
                    .put("sizeBytes", cube.size)
                    .put("sha256", sha256(cube))
            ))
        File(directory, "manifest.json").writeText(entry.toString())
        writeInstalled(packId)
        val pack = PackManifestRepo.RemotePack(
            id = packId,
            name = "Test LUT Pack",
            category = "filters",
            version = 1,
            sizeBytes = 1,
            url = "https://example.invalid/test.zip",
            sha256 = "0".repeat(64),
            entry = "manifest.json",
            itemCount = 1,
            payloadType = DownloadedLutFilterRepository.PAYLOAD_TYPE
        )
        return PackManifestRepo.PackManifestFile(packs = listOf(pack))
    }

    private fun validCube(): ByteArray = buildString {
        appendLine("TITLE \"Test Warm\"")
        appendLine("LUT_3D_SIZE 2")
        appendLine("DOMAIN_MIN 0 0 0")
        appendLine("DOMAIN_MAX 1 1 1")
        for (b in 0..1) for (g in 0..1) for (r in 0..1) {
            appendLine("${(r * 0.8f + 0.1f)} ${g.toFloat()} ${b.toFloat()}")
        }
    }.toByteArray(Charsets.UTF_8)

    private fun writeInstalled(id: String) {
        val installed = JSONObject().put(
            "packs",
            JSONObject().put(id, JSONObject().put("version", 1).put("sha256", "0".repeat(64)))
        )
        File(PackManager.packsRoot(context), "installed.json").writeText(installed.toString())
    }

    private fun removeInstalled(id: String) {
        val file = File(PackManager.packsRoot(context), "installed.json")
        val packs = if (file.isFile) JSONObject(file.readText()).optJSONObject("packs") ?: JSONObject() else JSONObject()
        packs.remove(id)
        file.writeText(JSONObject().put("packs", packs).toString())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
