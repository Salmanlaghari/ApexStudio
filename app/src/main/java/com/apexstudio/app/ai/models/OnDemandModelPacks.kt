package com.apexstudio.app.ai.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicitly requested, local-only AI model packs. Nothing in this object runs at
 * app startup: call [install] only from a user-initiated feature/download action.
 * Model bytes are stored under Context.filesDir, never external/shared storage.
 */
class OnDemandModelPacks private constructor(
    private val privateRoot: File,
    private val licenseAsset: (String) -> InputStream,
    private val packs: Map<String, ModelPack>,
    private val connectionFor: (URL) -> HttpURLConnection
) {
    data class Artifact(
        val path: String,
        val bytes: Long,
        val sha256: String,
        val url: String
    )

    data class ModelPack(
        val id: String,
        val revision: String,
        val license: String,
        val licenseAssetPath: String,
        val sourceRevision: String?,
        val artifacts: List<Artifact>
    ) {
        val totalBytes: Long get() = artifacts.sumOf { it.bytes }
    }

    data class Progress(val completedBytes: Long, val totalBytes: Long)

    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Metadata suitable for an explicit download-choice UI; no network or disk access. */
    fun availablePacks(): List<ModelPack> = packs.values.toList()

    /** Returns a complete verified install, or null; this method never downloads. */
    suspend fun installedDirectory(packId: String): File? = withContext(Dispatchers.IO) {
        val pack = packs[packId] ?: return@withContext null
        val target = installDirectory(pack)
        if (verifyInstalled(pack, target)) target else null
    }

    /** Locate a fully verified installed asset by its original APK-relative path; never downloads. */
    suspend fun installedFileForAsset(assetPath: String): File? = withContext(Dispatchers.IO) {
        val relative = assetPath.removePrefix("models/")
        val pack = packs.values.firstOrNull { candidate -> candidate.artifacts.any { it.path == relative } }
            ?: return@withContext null
        val directory = installDirectory(pack)
        if (!verifyInstalled(pack, directory)) return@withContext null
        safeChild(directory, relative).takeIf { it.isFile }
    }

    /** Synchronous counterpart for existing asset-stream APIs; call on an IO worker. */
    fun verifiedInstalledFileForAsset(assetPath: String): File? {
        val relative = assetPath.removePrefix("models/")
        val pack = packs.values.firstOrNull { candidate -> candidate.artifacts.any { it.path == relative } }
            ?: return null
        val artifact = pack.artifacts.first { it.path == relative }
        val file = safeChild(installDirectory(pack), relative)
        return file.takeIf { it.isFile && it.length() == artifact.bytes && runCatching { it.sha256() == artifact.sha256 }.getOrDefault(false) }
    }

    /** Delete a downloaded pack from the app-private model store. */
    suspend fun remove(packId: String): Boolean = withContext(Dispatchers.IO) {
        val pack = packs[packId] ?: return@withContext false
        val directory = File(privateRoot, pack.id)
        !directory.exists() || directory.deleteRecursively()
    }

    /**
     * Downloads one known pack after the caller's explicit request, checks every
     * byte count and SHA-256, copies its in-APK license, then atomically publishes
     * the complete directory with a same-filesystem rename. Cancellation or any
     * failure removes staging data and leaves no partially installed pack.
     */
    suspend fun install(packId: String, onProgress: (Progress) -> Unit = {}): File {
        val pack = requireNotNull(packs[packId]) { "Unknown AI model pack" }
        return locks.getOrPut(packId) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                validate(pack)
                check(privateRoot.isDirectory || privateRoot.mkdirs()) { "Cannot create private model directory" }
                check(privateRoot.canonicalFile == privateRoot.absoluteFile.canonicalFile) { "Invalid model directory" }
                val target = installDirectory(pack)
                if (verifyInstalled(pack, target)) return@withContext target
                if (target.exists()) target.deleteRecursively()
                val staging = File(privateRoot, ".staging-${pack.id}-${UUID.randomUUID()}")
                check(staging.mkdir()) { "Cannot create model staging directory" }
                var completed = 0L
                try {
                    for (artifact in pack.artifacts) {
                        currentCoroutineContext().ensureActive()
                        val output = safeChild(staging, artifact.path)
                        check(output.parentFile?.isDirectory == true || output.parentFile?.mkdirs() == true) {
                            "Cannot create model staging path"
                        }
                        val part = File(output.parentFile, output.name + ".part")
                        try {
                            completed += downloadArtifact(artifact, part, completed, pack.totalBytes, onProgress)
                            check(part.renameTo(output)) { "Cannot publish verified model file" }
                        } finally {
                            part.delete()
                        }
                    }
                    val license = safeChild(staging, "LICENSE.txt")
                    licenseAsset(pack.licenseAssetPath).use { source ->
                        FileOutputStream(license).use { destination ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                copied += count
                                require(copied <= MAX_LICENSE_BYTES) { "License file exceeds size limit" }
                                destination.write(buffer, 0, count)
                            }
                            destination.fd.sync()
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    check(verifyInstalled(pack, staging)) { "Installed model pack failed verification" }
                    check(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true) {
                        "Cannot create model install directory"
                    }
                    check(staging.renameTo(target)) { "Cannot atomically publish model pack" }
                    onProgress(Progress(pack.totalBytes, pack.totalBytes))
                    target
                } catch (failure: Throwable) {
                    staging.deleteRecursively()
                    throw failure
                }
            }
        }
    }

    private suspend fun downloadArtifact(
        artifact: Artifact,
        destination: File,
        alreadyDone: Long,
        total: Long,
        onProgress: (Progress) -> Unit
    ): Long {
        val connection = openSecureConnection(URL(artifact.url))
        try {
            var received = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        currentCoroutineContext().ensureActive()
                        if (count < 0) break
                        received += count
                        check(received <= artifact.bytes) { "Model download exceeded pinned size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(Progress(alreadyDone + received, total))
                    }
                    output.fd.sync()
                }
            }
            check(received == artifact.bytes) { "Model size mismatch for ${artifact.path}" }
            val actualHash = digest.digest().toHex()
            check(actualHash == artifact.sha256) { "Model SHA-256 mismatch for ${artifact.path}" }
            return received
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun openSecureConnection(source: URL): HttpURLConnection {
        var target = source
        repeat(6) { hop ->
            currentCoroutineContext().ensureActive()
            check(target.protocol.equals("https", ignoreCase = true)) { "Insecure model redirect refused" }
            val connection = connectionFor(target)
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "ApexStudio-ModelPacks/1.0")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    check(hop < 5) { "Too many model download redirects" }
                    val location = connection.getHeaderField("Location")
                        ?: error("Model redirect has no destination")
                    target = URL(target, location)
                    check(target.protocol.equals("https", ignoreCase = true)) { "Insecure model redirect refused" }
                    connection.disconnect()
                } else {
                    check(status in 200..299) { "Model download failed (HTTP $status)" }
                    return connection
                }
            } catch (failure: Throwable) {
                connection.disconnect()
                throw failure
            }
        }
        error("Too many model download redirects")
    }

    private fun installDirectory(pack: ModelPack): File = File(File(privateRoot, pack.id), pack.revision)

    private fun verifyInstalled(pack: ModelPack, directory: File): Boolean {
        return try {
            if (!directory.isDirectory) false
            else if (!File(directory, "LICENSE.txt").isFile || File(directory, "LICENSE.txt").length() !in 1..MAX_LICENSE_BYTES) false
            else pack.artifacts.all { artifact ->
                val file = safeChild(directory, artifact.path)
                file.isFile && file.length() == artifact.bytes && file.sha256() == artifact.sha256
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun safeChild(parent: File, relativePath: String): File {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) { "Invalid model path" }
        require(relativePath.split('/').all { it.isNotBlank() && it != "." && it != ".." }) { "Invalid model path" }
        val child = File(parent, relativePath)
        val prefix = parent.canonicalPath + File.separator
        require(child.canonicalPath.startsWith(prefix)) { "Model path escapes private storage" }
        return child
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_LICENSE_BYTES = 1024L * 1024L
        private const val MAX_PACK_BYTES = 150L * 1024L * 1024L
        private const val MAX_ARTIFACT_BYTES = 100L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000

        /** Catalog limited to immutable upstream revisions and license notices retained in APK assets. */
        val catalog: Map<String, ModelPack> = listOf(
            ModelPack(
                id = "whisper-tiny-multilingual",
                revision = "65176e2deb88badc814a94058666cadccc29b61c",
                license = "MIT",
                licenseAssetPath = "models/whisper/LICENSE",
                sourceRevision = "65176e2deb88badc814a94058666cadccc29b61c",
                artifacts = listOf(
                    Artifact("whisper/tiny-encoder.int8.onnx", 12_937_772, "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c/tiny-encoder.int8.onnx"),
                    Artifact("whisper/tiny-decoder.int8.onnx", 89_855_401, "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c/tiny-decoder.int8.onnx"),
                    Artifact("whisper/tiny-tokens.txt", 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c/tiny-tokens.txt")
                )
            ),
            ModelPack(
                id = "english-word-alignment",
                revision = "729c1a6730fb549c20a1c73a3d3f96f11020225e",
                license = "Apache-2.0",
                licenseAssetPath = "models/alignment/LICENSE",
                sourceRevision = "729c1a6730fb549c20a1c73a3d3f96f11020225e",
                artifacts = listOf(
                    Artifact("alignment/wav2vec2-base-960h-int8.onnx", 95_212_816, "1d9a366c27b2966625cd5035ac3db8847c53bba617169912bb251b42975a3a22", "https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/resolve/729c1a6730fb549c20a1c73a3d3f96f11020225e/onnx/model_quantized.onnx"),
                    Artifact("alignment/vocab.json", 358, "4178db26b3c7570f6a47f14ac6a1c7b32950b8c2800fb097287e53776934f1c5", "https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/resolve/729c1a6730fb549c20a1c73a3d3f96f11020225e/vocab.json"),
                    Artifact("alignment/preprocessor_config.json", 215, "8cdfd65ff4115423185a1512bdae100e2e0cd744f5b322417429944aaafd0827", "https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/resolve/729c1a6730fb549c20a1c73a3d3f96f11020225e/preprocessor_config.json"),
                    Artifact("alignment/config.json", 2_157, "15c7cf6378153bdcb33fce27780ab9aae37fd154fbb674cacc3347992055d323", "https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/resolve/729c1a6730fb549c20a1c73a3d3f96f11020225e/config.json")
                )
            ),
            ModelPack(
                id = "super-resolution-espcn",
                revision = "395472c4b6b92f788357237983d83fd913cfe7e3",
                license = "Apache-2.0",
                licenseAssetPath = "models/super_resolution/LICENSE",
                sourceRevision = "395472c4b6b92f788357237983d83fd913cfe7e3",
                artifacts = listOf(
                    Artifact("super_resolution/super-resolution-10.onnx", 240_078, "85f36ff88cc504a24af5e0602148bc56a8aa09a58eca8c0da2756f3e8186035e", "https://huggingface.co/onnxmodelzoo/super-resolution-10/resolve/395472c4b6b92f788357237983d83fd913cfe7e3/super-resolution-10.onnx")
                )
            ),
            ModelPack(
                id = "mediapipe-vision-models",
                revision = "b4dc9e2291d19aeea32a20a27c9324411b364bcb0c3f6c221f8bd9e64279bd24",
                license = "Apache-2.0",
                licenseAssetPath = "models/mediapipe/LICENSE",
                sourceRevision = null,
                artifacts = listOf(
                    Artifact("mediapipe/mediapipe-face-landmarker.task", 3_758_596, "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff", "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"),
                    Artifact("mediapipe/mediapipe-pose-landmarker.task", 5_777_746, "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a", "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"),
                    Artifact("mediapipe/mediapipe-hand-landmarker.task", 7_819_105, "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1", "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"),
                    Artifact("mediapipe/selfie_segmenter.tflite", 249_537, "191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b", "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite")
                )
            ),
            ModelPack(
                id = "modnet-background-removal",
                revision = "5069a5e306b9f5e9f4f2b0360264c9f8ea13b257c7c39943c7cf6a2ec3a102ae",
                license = "Apache-2.0",
                licenseAssetPath = "models/background_removal/LICENSE-Apache-2.0.txt",
                sourceRevision = null,
                artifacts = listOf(Artifact("background_removal/modnet_photographic.onnx", 25_969_398, "5069a5e306b9f5e9f4f2b0360264c9f8ea13b257c7c39943c7cf6a2ec3a102ae", "https://github.com/yakhyo/modnet/releases/download/weights/modnet_photographic.onnx"))
            )
        ).associateBy { it.id }.also { catalog -> catalog.values.forEach(::validate) }

        fun forApp(context: Context): OnDemandModelPacks = OnDemandModelPacks(
            privateRoot = File(context.applicationContext.filesDir, "ai-model-packs"),
            licenseAsset = { path -> context.applicationContext.assets.open(path) },
            packs = catalog,
            connectionFor = { url -> url.openConnection() as HttpURLConnection }
        )

        private fun validate(pack: ModelPack) {
            require(pack.id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}"))) { "Invalid model pack id" }
            require(pack.revision.matches(Regex("[a-f0-9]{40,64}"))) { "Model pack revision must be immutable" }
            if (pack.sourceRevision != null) require(pack.sourceRevision.matches(Regex("[a-f0-9]{40,64}"))) { "Invalid source revision" }
            require(pack.license in setOf("MIT", "Apache-2.0", "BSD-3-Clause")) { "Unrecognized model license" }
            require(pack.licenseAssetPath.startsWith("models/") && !pack.licenseAssetPath.contains("..")) { "Invalid license asset path" }
            require(pack.artifacts.isNotEmpty() && pack.totalBytes in 1..MAX_PACK_BYTES) { "Model pack size is outside safe bounds" }
            require(pack.artifacts.map { it.path }.toSet().size == pack.artifacts.size) { "Duplicate model path" }
            for (artifact in pack.artifacts) {
                require(artifact.path.split('/').all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it != "." && it != ".." }) { "Invalid model path" }
                require(artifact.bytes in 1..MAX_ARTIFACT_BYTES) { "Model artifact size is outside safe bounds" }
                require(artifact.sha256.matches(Regex("[a-f0-9]{64}"))) { "Model artifact hash must be pinned" }
                val url = URL(artifact.url)
                require(url.protocol == "https" && url.host.isNotBlank()) { "Model source must use HTTPS" }
                if (pack.sourceRevision != null && artifact.url.contains("huggingface.co")) {
                    require(artifact.url.contains("/resolve/${pack.sourceRevision}/")) { "Model source URL must pin its immutable revision" }
                }
            }
        }

        private fun File.sha256(): String = inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
