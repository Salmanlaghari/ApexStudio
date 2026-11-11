package com.apexstudio.app.data.stickers

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import com.airbnb.lottie.LottieCompositionFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimatedStickerPackIntegrityTest {
    @Test
    fun `catalog and all animated sticker archives match pinned per-file metadata`() {
        val root = findRepositoryRoot()
        val json = Json { ignoreUnknownKeys = true }
        val catalog = json.parseToJsonElement(File(root, "packs/manifest.json").readText()).jsonObject
        val packs = catalog.getValue("packs").jsonArray.map { it.jsonObject }
            .filter { it.getValue("category").jsonPrimitive.content == "stickers" }
        assertEquals("The gallery should expose four scalable 25-item packs", 4, packs.size)

        val stickerIds = mutableSetOf<String>()
        val motifKeys = mutableSetOf<String>()
        val categoryCounts = mutableMapOf<String, Int>()
        packs.forEach { pack ->
            val url = URI(pack.getValue("url").jsonPrimitive.content)
            assertEquals("raw.githubusercontent.com", url.host)
            assertEquals("https", url.scheme)
            val relativePath = url.path.substringAfter("/main/")
            assertTrue(relativePath.startsWith("remote-assets/stickers/animated/"))
            val zipFile = File(root, relativePath)
            val archiveBytes = zipFile.readBytes()
            assertEquals(pack.getValue("sizeBytes").jsonPrimitive.long, archiveBytes.size.toLong())
            assertEquals(pack.getValue("sha256").jsonPrimitive.content, sha256(archiveBytes))

            ZipFile(zipFile).use { archive ->
                val packManifest = json.parseToJsonElement(
                    archive.getInputStream(archive.getEntry("manifest.json")).bufferedReader().use { it.readText() }
                ).jsonObject
                val entries = packManifest.getValue("stickers").jsonArray
                assertEquals(pack.getValue("itemCount").jsonPrimitive.int, entries.size)
                entries.forEach { value ->
                    val entry = value.jsonObject
                    val fileName = entry.getValue("file").jsonPrimitive.content
                    assertTrue("Sticker file must stay at the pack root", File(fileName).name == fileName && !fileName.contains(".."))
                    val zipEntry = archive.getEntry(fileName) ?: error("Missing $fileName in ${zipFile.name}")
                    assertTrue("Sticker file must be a regular archive entry", !zipEntry.isDirectory)
                    val payload = archive.getInputStream(zipEntry).use { it.readBytes() }
                    assertEquals(entry.getValue("bytes").jsonPrimitive.long, payload.size.toLong())
                    assertEquals(entry.getValue("sha256").jsonPrimitive.content, sha256(payload))

                    val id = entry.getValue("id").jsonPrimitive.content
                    val motif = entry.getValue("motifKey").jsonPrimitive.content
                    assertTrue("Duplicate sticker id: $id", stickerIds.add(id))
                    assertTrue("Duplicate visual motif: $motif", motifKeys.add(motif))
                    val category = entry.getValue("category").jsonPrimitive.content
                    categoryCounts[category] = (categoryCounts[category] ?: 0) + 1

                    val lottie = json.parseToJsonElement(payload.decodeToString()).jsonObject
                    assertEquals(256, lottie.getValue("w").jsonPrimitive.int)
                    assertEquals(256, lottie.getValue("h").jsonPrimitive.int)
                    assertEquals(30.0, lottie.getValue("fr").jsonPrimitive.double, 0.0)
                    assertEquals(60.0, lottie.getValue("op").jsonPrimitive.double, 0.0)
                    assertTrue(lottie.getValue("layers").jsonArray.isNotEmpty())
                    val composition = archive.getInputStream(zipEntry).use {
                        LottieCompositionFactory.fromJsonInputStreamSync(
                            it, "sticker-integrity:${zipFile.name}:$fileName"
                        ).value
                    }
                    assertTrue("Lottie runtime rejected $fileName", composition != null)
                }
            }
        }
        assertEquals(100, stickerIds.size)
        assertEquals(100, motifKeys.size)
        assertEquals(setOf("Love", "Party", "Nature", "Animals", "Food", "Travel", "Sports", "Reactions", "Symbols", "Tech"), categoryCounts.keys)
        assertTrue(categoryCounts.values.all { it == 10 })
    }

    private fun findRepositoryRoot(): File {
        var current: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(10) {
            val candidate = current ?: error("Repository root not found")
            if (File(candidate, "packs/manifest.json").isFile) return candidate
            current = candidate.parentFile
        }
        error("Could not locate packs/manifest.json from ${System.getProperty("user.dir")}")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
