package com.apexstudio.app.data.filter

import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedLutPackIntegrityTest {
    @Test
    fun `cataloged downloadable LUT archive and every cube are integrity pinned and parseable`() {
        val repoRoot = findRepoRoot()
        val catalogFile = File(repoRoot, "packs/manifest.json")
        val catalog = Json { ignoreUnknownKeys = true }.decodeFromString<PackManifestRepo.PackManifestFile>(catalogFile.readText())
        val pack = catalog.packs.single { it.id == "lut-filters-vol-01" }
        assertEquals(DownloadedLutFilterRepository.PAYLOAD_TYPE, pack.payloadType)
        assertEquals(30, pack.itemCount)

        val archive = File(repoRoot, "remote-assets/filters/apexstudio-lut-filters-vol-01.zip")
        assertEquals(pack.sizeBytes, archive.length())
        assertEquals(pack.sha256, sha256(archive.readBytes()))

        ZipFile(archive).use { zip ->
            val archiveManifest = zip.getInputStream(zip.getEntry(pack.entry)).bufferedReader().use { it.readText() }
            val payload = org.json.JSONObject(archiveManifest)
            assertEquals(1, payload.getInt("schemaVersion"))
            assertTrue(payload.getString("attribution").contains("Original procedural LUTs"))
            val filters = payload.getJSONArray("filters")
            assertEquals(pack.itemCount, filters.length())
            val expectedMembers = mutableSetOf(pack.entry)
            val hashes = mutableSetOf<String>()

            for (index in 0 until filters.length()) {
                val item = filters.getJSONObject(index)
                val fileName = item.getString("file")
                assertTrue("unsafe archive path: $fileName", fileName.isNotBlank() && !fileName.startsWith("/") &&
                    fileName.split('/').none { it.isBlank() || it == "." || it == ".." } && '\\' !in fileName)
                expectedMembers += fileName
                val entry = zip.getEntry(fileName)
                assertNotNull("missing LUT $fileName", entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                assertEquals(item.getLong("sizeBytes"), bytes.size.toLong())
                assertTrue("oversized LUT $fileName", bytes.size <= 1024 * 1024)
                val digest = sha256(bytes)
                assertEquals(item.getString("sha256"), digest)
                hashes += digest
                assertTrue("LUT $fileName must declare a 17-cube", bytes.toString(Charsets.UTF_8).contains("LUT_3D_SIZE 17"))

                val values = CubeLutParser.parse(
                    BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
                )
                assertNotNull("LUT parser rejected $fileName", values)
                assertEquals(17 * 17 * 17 * 3, values!!.size)
                assertTrue("non-finite channel in $fileName", values.all { it.isFinite() })
                assertTrue("out-of-range channel in $fileName", values.all { it in 0f..1f })
            }

            val actualMembers = mutableSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) actualMembers += entries.nextElement().name
            assertEquals(expectedMembers, actualMembers)
            assertEquals("Every LUT should be a distinct asset", pack.itemCount, hashes.size)
        }
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
