package com.apexstudio.app.ai.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnDemandModelPacksTest {
    @Test
    fun successfulHttpsInstallVerifiesBytesLicenseAndPublishesOnlyCompleteFiles() = runBlocking {
        withHarness(bytes = "verified model bytes".toByteArray()) { harness ->
            val enteredRead = CountDownLatch(1)
            val releaseRead = CountDownLatch(1)
            val bytes = "verified model bytes".toByteArray()
            harness.response = { gatedInput(bytes, enteredRead, releaseRead) }

            val installation = async(Dispatchers.IO) { harness.store.install(harness.pack.id) }
            assertTrue("download did not reach its first read", enteredRead.await(5, TimeUnit.SECONDS))
            val finalDirectory = harness.installDirectory
            assertFalse("an incomplete pack must not be visible at its final path", finalDirectory.exists())
            assertTrue("download must stage privately", harness.stagingDirectories().isNotEmpty())
            releaseRead.countDown()

            val installed = installation.await()
            assertEquals(finalDirectory.canonicalFile, installed.canonicalFile)
            assertArrayEquals(bytes, File(installed, harness.artifact.path).readBytes())
            assertEquals(bytes.size.toLong(), File(installed, harness.artifact.path).length())
            assertEquals(sha256(bytes), sha256(File(installed, harness.artifact.path).readBytes()))
            assertArrayEquals(harness.licenseBytes, File(installed, "LICENSE.txt").readBytes())
            assertTrue("the response must have been disconnected", harness.lastConnection?.disconnected == true)
            assertNoPartialOrStagingFiles(harness)
            assertEquals(finalDirectory.canonicalFile, harness.store.installedDirectory(harness.pack.id)?.canonicalFile)
        }
    }

    @Test
    fun oversizedArtifactResponseWrongHashAndTruncationAllFailWithoutPublishing() = runBlocking {
        val validBytes = "pinned-size".toByteArray()
        val cases = listOf(
            ResponseCase(validBytes + byteArrayOf(1), validBytes.size.toLong(), sha256(validBytes), "oversize"),
            ResponseCase(validBytes, validBytes.size.toLong(), "0".repeat(64), "wrong-hash"),
            ResponseCase(validBytes.copyOf(validBytes.size - 1), validBytes.size.toLong(), sha256(validBytes), "truncated")
        )
        for ((payload, pinnedSize, pinnedHash, label) in cases) {
            withHarness(bytes = validBytes, pinnedBytes = pinnedSize, pinnedHash = pinnedHash) { harness ->
                harness.response = { ByteArrayInputStream(payload) }
                assertFailure("$label response should be rejected") { runBlocking { harness.store.install(harness.pack.id) } }
                assertFalse(harness.installDirectory.exists())
                assertNoPartialOrStagingFiles(harness)
                assertTrue("$label test should exercise the fake connection", harness.connections.get() == 1)
            }
        }
    }

    @Test
    fun configuredArtifactAboveHardSizeLimitIsRejectedBeforeOpeningConnection() = runBlocking {
        withHarness(pinnedBytes = 100L * 1024L * 1024L + 1L) { harness ->
            assertFailure("artifact exceeding the configured 100 MiB cap should be rejected") {
                harness.store.install(harness.pack.id)
            }
            assertEquals(0, harness.connections.get())
            assertFalse(harness.installDirectory.exists())
            assertNoPartialOrStagingFiles(harness)
        }
    }

    @Test
    fun insecureUrlAndTraversalPathAreRejectedBeforeNetworkOrStorage() = runBlocking {
        withHarness(url = "http://models.example.test/model.bin") { insecure ->
            assertFailure("HTTP model source must be rejected") { insecure.store.install(insecure.pack.id) }
            assertEquals(0, insecure.connections.get())
            assertNoPartialOrStagingFiles(insecure)
        }
        withHarness(path = "../outside.bin") { traversal ->
            assertFailure("traversal artifact path must be rejected") { traversal.store.install(traversal.pack.id) }
            assertEquals(0, traversal.connections.get())
            assertFalse(File(traversal.root.parentFile, "outside.bin").exists())
            assertNoPartialOrStagingFiles(traversal)
        }
    }

    @Test
    fun cancellationDuringDownloadRemovesStagingAndPartFiles() = runBlocking {
        withHarness { harness ->
            val enteredRead = CountDownLatch(1)
            val releaseRead = CountDownLatch(1)
            harness.response = { gatedInput("cancel-me".toByteArray(), enteredRead, releaseRead) }
            val installation = async(Dispatchers.IO) { harness.store.install(harness.pack.id) }
            assertTrue("download did not reach its first read", enteredRead.await(5, TimeUnit.SECONDS))
            assertFalse(harness.installDirectory.exists())
            assertTrue(harness.stagingDirectories().isNotEmpty())
            installation.cancel()
            releaseRead.countDown()
            try {
                installation.await()
                fail("cancelled install unexpectedly completed")
            } catch (_: CancellationException) {
                // Expected: install must observe cancellation after the blocked read returns.
            }
            assertFalse(harness.installDirectory.exists())
            assertNoPartialOrStagingFiles(harness)
        }
    }

    @Test
    fun corruptedCacheIsNotReportedInstalledAndExplicitInstallReplacesIt() = runBlocking {
        withHarness { harness ->
            harness.response = { ByteArrayInputStream(harness.bytes) }
            val installed = harness.store.install(harness.pack.id)
            File(installed, harness.artifact.path).writeBytes("corrupt".toByteArray())
            assertNull(harness.store.installedDirectory(harness.pack.id))
            assertNull(harness.store.installedFileForAsset("models/${harness.artifact.path}"))

            val replacement = harness.store.install(harness.pack.id)
            assertEquals(installed.canonicalFile, replacement.canonicalFile)
            assertArrayEquals(harness.bytes, File(replacement, harness.artifact.path).readBytes())
            assertEquals(2, harness.connections.get())
            assertNoPartialOrStagingFiles(harness)
        }
    }

    @Test
    fun removeDeletesPackAndUnknownPackDoesNotStartNetwork() = runBlocking {
        withHarness { harness ->
            harness.response = { ByteArrayInputStream(harness.bytes) }
            harness.store.install(harness.pack.id)
            assertTrue(harness.store.remove(harness.pack.id))
            assertNull(harness.store.installedDirectory(harness.pack.id))
            assertFalse(harness.installDirectory.parentFile?.exists() == true)
            assertFalse(harness.store.remove("not-a-pack"))
            assertEquals(1, harness.connections.get())
        }
    }

    @Test
    fun installedChecksAndCatalogListingNeverOpenNetworkConnections() = runBlocking {
        withHarness { harness ->
            assertNull(harness.store.installedDirectory(harness.pack.id))
            assertNull(harness.store.installedFileForAsset("models/${harness.artifact.path}"))
            assertNull(harness.store.verifiedInstalledFileForAsset("models/${harness.artifact.path}"))
            assertEquals(listOf(harness.pack), harness.store.availablePacks())
            assertEquals(0, harness.connections.get())

            val installed = harness.installDirectory.apply { mkdirs() }
            File(installed, harness.artifact.path).apply {
                parentFile?.mkdirs()
                writeBytes(harness.bytes)
            }
            File(installed, "LICENSE.txt").writeBytes(harness.licenseBytes)
            assertEquals(installed.canonicalFile, harness.store.installedDirectory(harness.pack.id)?.canonicalFile)
            assertEquals(File(installed, harness.artifact.path).canonicalFile,
                harness.store.installedFileForAsset("models/${harness.artifact.path}")?.canonicalFile)
            assertEquals(File(installed, harness.artifact.path).canonicalFile,
                harness.store.verifiedInstalledFileForAsset("models/${harness.artifact.path}")?.canonicalFile)
            assertEquals(0, harness.connections.get())
        }
    }

    @Test
    fun literalCatalogMetadataMatchesEveryCheckedInModelsLockRecord() {
        val lock = RuntimeEnvironment.getApplication().assets.open("models/models.lock.json").bufferedReader().use { it.readText() }
        val rows = JSONObject(lock).getJSONArray("models")
        val locked = (0 until rows.length()).map { rows.getJSONObject(it) }.associateBy { it.getString("path") }
        val catalogArtifacts = OnDemandModelPacks.catalog.values.flatMap { pack ->
            pack.artifacts.map { artifact -> Triple(pack, artifact, artifact.path) }
        }
        assertEquals("catalog and lock should inventory the same paths", locked.keys, catalogArtifacts.map { it.third }.toSet())
        for ((pack, artifact, path) in catalogArtifacts) {
            val row = requireNotNull(locked[path]) { "missing lock entry for $path" }
            assertEquals("byte count mismatch for $path", row.getLong("bytes"), artifact.bytes)
            assertEquals("SHA-256 mismatch for $path", row.getString("sha256"), artifact.sha256)
            assertEquals("source URL mismatch for $path", row.getString("source"), artifact.url)
            assertEquals("license mismatch for $path", row.getString("license"), pack.license)
            assertEquals("license asset mismatch for $path", row.getString("licenseFile"), pack.licenseAssetPath.removePrefix("models/"))
        }
    }

    @Test fun verifiedHttpsRedirectCanInstallWithoutAutomaticRedirects() = runBlocking {
        withHarness { harness ->
            harness.redirectFor = { url -> if (url.host == "models.example.test") "https://cdn.example.test/model.bin" else null }
            val directory = harness.store.install(harness.pack.id)
            assertTrue(directory.isDirectory)
            assertEquals(2, harness.connections.get())
            assertFalse(harness.lastConnection!!.instanceFollowRedirects)
        }
    }

    @Test fun httpsToHttpDowngradeIsRejectedBeforeSecondConnection() = runBlocking {
        withHarness { harness ->
            harness.redirectFor = { "http://insecure.example.test/model.bin" }
            assertFailure("HTTP redirect accepted") { harness.store.install(harness.pack.id) }
            assertEquals(1, harness.connections.get())
            assertFalse(harness.installDirectory.exists())
            assertNoPartialOrStagingFiles(harness)
        }
    }

    @Test fun redirectLoopIsBoundedAndCleansStaging() = runBlocking {
        withHarness { harness ->
            harness.redirectFor = { it.toString() }
            assertFailure("Redirect loop was not stopped") { harness.store.install(harness.pack.id) }
            assertEquals(6, harness.connections.get())
            assertNoPartialOrStagingFiles(harness)
        }
    }

    private suspend fun withHarness(
        bytes: ByteArray = "model-fixture".toByteArray(),
        pinnedBytes: Long = bytes.size.toLong(),
        pinnedHash: String = sha256(bytes),
        path: String = "fixture/model.bin",
        url: String = "https://models.example.test/model.bin",
        block: suspend (Harness) -> Unit
    ) {
        val root = Files.createTempDirectory("ondemand-model-packs-").toFile()
        try {
            val harness = Harness(root, bytes, path, pinnedBytes, pinnedHash, url)
            block(harness)
        } finally {
            root.deleteRecursively()
        }
    }

    private inner class Harness(
        val root: File,
        val bytes: ByteArray,
        path: String,
        pinnedBytes: Long,
        pinnedHash: String,
        url: String
    ) {
        val licenseBytes = "fixture license notice\n".toByteArray()
        val connections = AtomicInteger()
        var response: () -> InputStream = { ByteArrayInputStream(bytes) }
        var redirectFor: (URL) -> String? = { null }
        var lastConnection: FakeConnection? = null
        val artifact = OnDemandModelPacks.Artifact(path, pinnedBytes, pinnedHash, url)
        val pack = OnDemandModelPacks.ModelPack(
            id = "test-model-pack",
            revision = "0123456789abcdef0123456789abcdef01234567",
            license = "Apache-2.0",
            licenseAssetPath = "models/test/LICENSE",
            sourceRevision = null,
            artifacts = listOf(artifact)
        )
        val installDirectory = File(File(root, pack.id), pack.revision)
        val store = newStore(root, pack, licenseBytes) { requestedUrl ->
            connections.incrementAndGet()
            FakeConnection(requestedUrl, response, redirectFor(requestedUrl)).also { lastConnection = it }
        }

        fun stagingDirectories(): List<File> = root.listFiles().orEmpty().filter { it.name.startsWith(".staging-") }
    }

    private class FakeConnection(url: URL, private val response: () -> InputStream, private val redirect: String?) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = if (redirect == null) 200 else 302
        override fun getHeaderField(name: String?): String? = if (name.equals("Location", ignoreCase = true)) redirect else null
        override fun getInputStream(): InputStream = response()
    }

    private data class ResponseCase(
        val payload: ByteArray,
        val pinnedBytes: Long,
        val pinnedHash: String,
        val label: String
    )

    private fun newStore(
        root: File,
        pack: OnDemandModelPacks.ModelPack,
        license: ByteArray,
        connect: (URL) -> HttpURLConnection
    ): OnDemandModelPacks {
        // Deliberately exercise the production constructor without any HTTP implementation or test-only production hook.
        val constructor = OnDemandModelPacks::class.java.declaredConstructors.single { it.parameterCount == 4 }
        constructor.isAccessible = true
        val licenseProvider: (String) -> InputStream = { ByteArrayInputStream(license) }
        val connector: (URL) -> HttpURLConnection = { url -> connect(url) }
        return constructor.newInstance(root, licenseProvider, mapOf(pack.id to pack), connector) as OnDemandModelPacks
    }

    private fun gatedInput(bytes: ByteArray, enteredRead: CountDownLatch, releaseRead: CountDownLatch): InputStream =
        object : ByteArrayInputStream(bytes) {
            private var firstRead = true
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (firstRead) {
                    firstRead = false
                    enteredRead.countDown()
                    check(releaseRead.await(5, TimeUnit.SECONDS)) { "test did not release blocked input" }
                }
                return super.read(buffer, offset, length)
            }
        }

    private fun assertNoPartialOrStagingFiles(harness: Harness) {
        assertTrue("staging directory leaked: ${harness.stagingDirectories()}", harness.stagingDirectories().isEmpty())
        val parts = harness.root.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.toList()
        assertTrue("partial files leaked: $parts", parts.isEmpty())
    }

    private suspend fun assertFailure(message: String, block: suspend () -> Unit) {
        try {
            block()
            fail(message)
        } catch (_: IllegalArgumentException) {
            // Expected validation or content-integrity failure.
        } catch (_: IllegalStateException) {
            // Expected validation or content-integrity failure.
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
