package com.apexstudio.app.data.template

import android.content.Context
import com.apexstudio.app.data.packs.download.DownloadEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

@Serializable
data class ShortsMusicAsset(
    val id: String,
    val templateName: String,
    val trackTitle: String,
    val artist: String,
    val fileName: String,
    val downloadUrl: String,
    val sourcePageUrl: String,
    val sourceDownloadUrl: String,
    val mimeType: String,
    val bytes: Long,
    val durationMs: Long,
    val sha256: String,
    val license: String,
    val licenseUrl: String,
    val attributionRequired: Boolean,
    val attribution: String,
    val changes: String,
) {
    /** Full copyable credit, including any edits made when the template is applied. */
    val creditText: String
        get() = buildString {
            append(attribution.trim())
            if (changes.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("Changes: ").append(changes.trim())
            }
        }
}

@Serializable
data class ShortsMusicManifest(val version: Int, val assets: List<ShortsMusicAsset>)

data class ShortsMusicProgress(val assetId: String, val doneBytes: Long, val totalBytes: Long)

sealed interface ShortsMusicDownloadResult {
    data class Success(val asset: ShortsMusicAsset, val file: File) : ShortsMusicDownloadResult
    data class Failed(val reason: String, val canRetry: Boolean = true, val cause: Throwable? = null) : ShortsMusicDownloadResult
}

internal fun interface ShortsMusicTransfer {
    suspend fun download(
        url: String,
        destination: File,
        expectedSha256: String,
        onProgress: (Long, Long) -> Unit,
    ): DownloadEngine.Result
}

/** Loads the small metadata manifest; audio bytes are intentionally remote-only. */
class ShortsMusicRepository private constructor(
    private val context: Context,
    private val manifestLoader: () -> ShortsMusicManifest,
    private val transfer: ShortsMusicTransfer,
) {
    constructor(context: Context) : this(
        context = context,
        manifestLoader = {
            val raw = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
            parseAndValidateManifest(raw)
        },
        transfer = ShortsMusicTransfer { url, destination, expectedSha256, onProgress ->
            DownloadEngine.download(url, destination, expectedSha256, onProgress)
        },
    )

    internal constructor(
        context: Context,
        manifest: ShortsMusicManifest,
        transfer: ShortsMusicTransfer,
    ) : this(
        context = context,
        manifestLoader = { parseAndValidateManifest(Json.encodeToString(manifest)) },
        transfer = transfer,
    )

    private val progressState = MutableStateFlow<ShortsMusicProgress?>(null)
    val progress: StateFlow<ShortsMusicProgress?> = progressState

    fun loadManifest(): ShortsMusicManifest = manifestLoader()

    suspend fun download(assetId: String, onProgress: (ShortsMusicProgress) -> Unit = {}): ShortsMusicDownloadResult {
        val asset = loadManifest().assets.firstOrNull { it.id == assetId }
            ?: return ShortsMusicDownloadResult.Failed("Unknown Shorts music asset: $assetId", canRetry = false)
        val destination = File(File(context.filesDir, MUSIC_DIRECTORY), asset.fileName)
        val callback = { done: Long, total: Long ->
            val update = ShortsMusicProgress(asset.id, done, if (total > 0L) total else asset.bytes)
            progressState.value = update
            onProgress(update)
        }
        return when (val result = transfer.download(asset.downloadUrl, destination, asset.sha256, callback)) {
            is DownloadEngine.Result.Success -> {
                if (result.file.length() != asset.bytes || !DownloadEngine.verifySha256(result.file, asset.sha256)) {
                    result.file.delete()
                    ShortsMusicDownloadResult.Failed("Integrity failure for ${asset.id}: byte count or SHA-256 mismatch", canRetry = true)
                } else ShortsMusicDownloadResult.Success(asset, result.file)
            }
            is DownloadEngine.Result.Failed -> ShortsMusicDownloadResult.Failed(result.reason, result.canRetry)
        }
    }

    companion object {
        fun loadCatalog(context: Context): List<ShortsMusicAsset> =
            ShortsMusicRepository(context).loadManifest().assets

        suspend fun download(
            context: Context,
            assetId: String,
            onProgress: (ShortsMusicProgress) -> Unit = {},
        ): ShortsMusicDownloadResult = ShortsMusicRepository(context).download(assetId, onProgress)

        const val MANIFEST_ASSET = "shorts/music_catalog.json"
        const val MUSIC_DIRECTORY = "shorts_music"
        const val ALLOWED_URL_PREFIX = "https://raw.githubusercontent.com/Salmanlaghari/ApexStudio/main/remote-assets/shorts/music/"
        private val FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private val SHA256 = Regex("[0-9a-fA-F]{64}")

        fun parseAndValidateManifest(raw: String): ShortsMusicManifest {
            val manifest = Json.decodeFromString<ShortsMusicManifest>(raw)
            require(manifest.version > 0) { "Unsupported Shorts music manifest version" }
            require(manifest.assets.size == 10) { "Expected 10 Shorts music assets" }
            require(manifest.assets.map { it.id }.distinct().size == manifest.assets.size) { "Duplicate music asset id" }
            manifest.assets.forEach { asset ->
                require(asset.id.isNotBlank() && asset.templateName.isNotBlank() && asset.trackTitle.isNotBlank() && asset.artist.isNotBlank()) { "Incomplete music metadata for ${asset.id}" }
                require(asset.license.isNotBlank() && asset.attribution.isNotBlank() && asset.changes.isNotBlank()) { "Incomplete license credit or change note for ${asset.id}" }
                require(asset.sourcePageUrl.startsWith("https://") && asset.sourceDownloadUrl.startsWith("https://") && asset.licenseUrl.startsWith("https://")) { "Music source and license links must use HTTPS for ${asset.id}" }
                require(FILE_NAME.matches(asset.fileName) && !asset.fileName.contains("..")) { "Unsafe music filename: ${asset.fileName}" }
                require(asset.downloadUrl == ALLOWED_URL_PREFIX + asset.fileName) { "Music URL outside allowlist: ${asset.downloadUrl}" }
                val uri = URI(asset.downloadUrl)
                require(uri.scheme == "https" && uri.host == "raw.githubusercontent.com" && uri.path.startsWith("/Salmanlaghari/ApexStudio/main/remote-assets/shorts/music/")) { "Invalid music URL: ${asset.downloadUrl}" }
                require(asset.mimeType.startsWith("audio/")) { "Invalid music MIME type for ${asset.id}" }
                require(asset.bytes > 0L && asset.durationMs >= 30_000L && SHA256.matches(asset.sha256)) { "Invalid size, duration, or SHA-256 for ${asset.id}" }
            }
            return manifest
        }
    }
}
