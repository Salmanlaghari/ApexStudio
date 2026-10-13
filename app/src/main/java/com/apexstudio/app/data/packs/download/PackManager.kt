package com.apexstudio.app.data.packs.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * On-demand pack manager (PR-C / "Blake").
 *
 * Owns the lifecycle of downloadable content packs:
 * manifest fetch → download (resumable, verified) → install → delete,
 * with per-pack UI state exposed as a [StateFlow].
 *
 * Storage: app-specific external files dir
 * (`…/Android/data/com.apexstudio.app/files/packs/<packId>/`) — no storage
 * permission needed, cleaned up automatically on uninstall.
 *
 * Feature gating: features that need a pack call [requirePack]; when it
 * returns false they show the download prompt instead of the feature —
 * graceful, no dead buttons, no crashes.
 */
object PackManager {

    private const val TAG = "PackManager"
    private const val REGISTRY_NAME = "installed.json"

    /** UI-facing state of a single pack. */
    sealed interface PackState {
        data object NotDownloaded : PackState
        data class Downloading(val doneBytes: Long, val totalBytes: Long) : PackState {
            val fraction: Float get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
        }
        data object Downloaded : PackState
        data object UpdateAvailable : PackState
        data class Failed(val reason: String) : PackState
        data object ComingSoon : PackState
    }

    /** One row in the Packs UI. */
    data class PackUiItem(
        val pack: PackManifestRepo.RemotePack,
        val state: PackState
    )

    @Serializable
    private data class InstalledEntry(val version: Int, val sha256: String)

    @Serializable
    private data class InstalledRegistry(val packs: Map<String, InstalledEntry> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Process-lifetime scope, intentionally never cancelled. PackManager is an
     * application singleton (Kotlin `object`, never garbage-collected), so
     * this is the standard application-scoped-coroutine pattern: the scope's
     * lifetime == the process lifetime, and there is nothing to leak.
     * Cancelling it would break in-flight downloads that must survive screen
     * navigation — the feature's core requirement. Individual downloads are
     * tracked in [jobs] and are cancellable via [cancel]/[delete]; each entry
     * is removed on completion, so the map cannot grow unboundedly.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _packsState = MutableStateFlow<List<PackUiItem>>(emptyList())
    /** Observed by the Packs UI and by feature panels showing downloadable packs. */
    val packsState: StateFlow<List<PackUiItem>> = _packsState.asStateFlow()

    private val _manifestError = MutableStateFlow<String?>(null)
    /** Non-null when the catalog couldn't be refreshed (network/offline); the UI shows it. */
    val manifestError: StateFlow<String?> = _manifestError.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    /** Surfaced when a download couldn't even start (e.g. pack missing from catalog). */
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    /** True while a catalog refresh is in flight; lets the UI show a spinner vs "empty". */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    /** Root dir for all downloaded packs. */
    fun packsRoot(context: Context): File {
        val base = context.getExternalFilesDir("packs") ?: File(context.filesDir, "packs")
        base.mkdirs()
        return base
    }

    /** Install dir for one pack. */
    fun packDir(context: Context, packId: String): File =
        File(packsRoot(context), packId)

    private fun registryFile(context: Context): File =
        File(packsRoot(context), REGISTRY_NAME)

    private fun readRegistry(context: Context): InstalledRegistry = try {
        val f = registryFile(context)
        if (!f.exists()) InstalledRegistry()
        else json.decodeFromString<InstalledRegistry>(f.readText())
    } catch (e: Exception) {
        Log.w(TAG, "Registry read failed", e)
        InstalledRegistry()
    }

    private fun writeRegistry(context: Context, registry: InstalledRegistry) = try {
        registryFile(context).writeText(json.encodeToString(InstalledRegistry.serializer(), registry))
    } catch (e: Exception) {
        Log.w(TAG, "Registry write failed", e)
    }

    /** True when the pack is installed (any version). Disk read — call off the main thread. */
    fun isDownloaded(context: Context, packId: String): Boolean =
        readRegistry(context).packs.containsKey(packId) &&
            packDir(context, packId).exists()

    /** Installed pack ids. Disk read — call off the main thread. */
    fun installedPackIds(context: Context): Set<String> =
        readRegistry(context).packs.keys

    /**
     * Feature-gating API. Returns true when the pack is ready to use.
     * When false, the caller must show the download prompt (see PacksScreen
     * and the downloadable-packs section in the filter panel) — never silently
     * proceed and never crash.
     *
     * Composition-safe: reads only the in-memory UI state (no disk I/O).
     * Callers must have triggered [refresh] first (PacksScreen and the filter
     * panel both do on open); before the first refresh an unknown pack reads
     * as not-downloaded, which is the safe default. For the on-disk truth
     * outside composition, use [isDownloaded] off the main thread.
     */
    fun requirePack(context: Context, packId: String): Boolean {
        val item = _packsState.value.find { it.pack.id == packId } ?: return false
        return item.state == PackState.Downloaded || item.state == PackState.UpdateAvailable
    }

    /** Refresh the UI state from manifest + registry. Safe to call often. */
    fun refresh(context: Context) {
        scope.launch { refreshSync(context) }
    }

    /**
     * Suspending refresh — callers that need the state populated before
     * continuing (e.g. feature panels on first open) await this instead of
     * [refresh].
     */
    suspend fun refreshSync(context: Context) {
        _isLoading.value = true
        try {
            val manifest = withContext(Dispatchers.IO) {
                PackManifestRepo.fetchManifest(context)
            }
            _manifestError.value = PackManifestRepo.lastFetchError
            val installed = withContext(Dispatchers.IO) { readRegistry(context).packs }
            _packsState.value = buildItems(context, manifest, installed)
        } finally {
            _isLoading.value = false
        }
    }

    private fun buildItems(
        context: Context,
        manifest: PackManifestRepo.PackManifestFile,
        installed: Map<String, InstalledEntry>
    ): List<PackUiItem> = manifest.packs.map { pack ->
        val state: PackState = when {
            !pack.isAvailable -> PackState.ComingSoon
            jobs.containsKey(pack.id) -> preserveDownloadProgress(context, pack)
            else -> {
                val entry = installed[pack.id]
                when {
                    entry == null -> PackState.NotDownloaded
                    entry.version < pack.version -> PackState.UpdateAvailable
                    else -> PackState.Downloaded
                }
            }
        }
        PackUiItem(pack, state)
    }

    /**
     * Keep the in-flight progress bar stable across refreshes. Seeds from the
     * previous UI state, falling back to the actual `.part` file size so a
     * refresh never visually resets progress to zero.
     */
    private fun preserveDownloadProgress(
        context: Context,
        pack: PackManifestRepo.RemotePack
    ): PackState.Downloading {
        (_packsState.value.find { it.pack.id == pack.id }?.state as? PackState.Downloading)
            ?.let { return it }
        val part = File(
            packDir(context, pack.id),
            DownloadEngine.fileNameFor(pack.url, pack.id) + ".part"
        )
        val done = if (part.exists()) part.length() else 0L
        return PackState.Downloading(done, pack.sizeBytes)
    }

    /** Dismiss the download-error banner shown by the Packs UI. */
    fun clearDownloadError() {
        _downloadError.value = null
    }

    /** Start (or resume) downloading a pack. No-op for coming-soon packs. */
    fun download(context: Context, packId: String) {
        val appContext = context.applicationContext
        jobs[packId]?.let { return } // already in flight
        scope.launch {
            // Ensure the catalog is loaded first — the tap always leads somewhere.
            var item = _packsState.value.find { it.pack.id == packId }
            if (item == null) {
                refreshSync(appContext)
                item = _packsState.value.find { it.pack.id == packId }
                if (item == null) {
                    // Still missing after a refresh: surface it instead of
                    // silently doing nothing.
                    _downloadError.value = "Pack \"$packId\" is not in the catalog right now."
                    return@launch
                }
            }
            val pack = item.pack
            if (!pack.isAvailable || jobs.containsKey(packId)) return@launch
            _downloadError.value = null
            startDownload(appContext, pack)
        }
    }

    private fun startDownload(appContext: Context, pack: PackManifestRepo.RemotePack) {
        val packId = pack.id
        val dir = packDir(appContext, packId)
        val fileName = DownloadEngine.fileNameFor(pack.url, packId)
        val destFile = File(dir, fileName)

        jobs[packId] = scope.launch {
            withContext(Dispatchers.IO) { dir.mkdirs() }
            updateItem(packId) { it.copy(state = PackState.Downloading(0, pack.sizeBytes)) }

            // Throttle progress emissions: the engine calls back per 8KB
            // chunk, which would otherwise rebuild the UI thousands of times
            // on a fast connection. StateFlow is thread-safe, so update it
            // directly — no per-callback coroutine launch.
            var lastEmitMs = 0L
            var lastPct = -1
            val result = DownloadEngine.download(
                url = pack.url,
                destFile = destFile,
                expectedSha256 = pack.sha256
            ) { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else -1
                val now = android.os.SystemClock.elapsedRealtime()
                if (pct != lastPct && (now - lastEmitMs >= 250 || done >= total)) {
                    lastPct = pct
                    lastEmitMs = now
                    // StateFlow is thread-safe: update directly, no coroutine hop.
                    updateItem(packId) {
                        if (it.state is PackState.Downloading)
                            it.copy(state = PackState.Downloading(done, total))
                        else it
                    }
                }
            }

            jobs.remove(packId)
            when (result) {
                is DownloadEngine.Result.Success -> {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            // Unpack zips; single files are used as-is.
                            if (destFile.extension.equals("zip", ignoreCase = true)) {
                                unzip(destFile, dir)
                                destFile.delete()
                            }
                            val reg = readRegistry(appContext)
                            writeRegistry(
                                appContext,
                                reg.copy(packs = reg.packs + (packId to InstalledEntry(pack.version, pack.sha256)))
                            )
                            // Let features pick up the new content (e.g. filter packs).
                            DownloadedPacks.onPackInstalled(appContext, packId)
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Pack install failed: $packId", e)
                            false
                        }
                    }
                    updateItem(packId) {
                        it.copy(state = if (ok) PackState.Downloaded else PackState.Failed("Install failed"))
                    }
                }
                is DownloadEngine.Result.Failed ->
                    updateItem(packId) { it.copy(state = PackState.Failed(result.reason)) }
            }
        }
    }

    /** Cancel an in-flight download (the `.part` file stays for resume). */
    fun cancel(context: Context, packId: String) {
        val appContext = context.applicationContext
        jobs.remove(packId)?.cancel()
        scope.launch {
            val downloaded = withContext(Dispatchers.IO) { isDownloaded(appContext, packId) }
            updateItem(packId) {
                it.copy(state = if (downloaded) PackState.Downloaded else PackState.NotDownloaded)
            }
        }
    }

    /** Delete a pack's files and registry entry. */
    fun delete(context: Context, packId: String) {
        val appContext = context.applicationContext
        cancel(appContext, packId)
        scope.launch {
            withContext(Dispatchers.IO) {
                packDir(appContext, packId).deleteRecursively()
                val reg = readRegistry(appContext)
                writeRegistry(appContext, reg.copy(packs = reg.packs - packId))
            }
            updateItem(packId) { it.copy(state = PackState.NotDownloaded) }
        }
    }

    private fun updateItem(packId: String, transform: (PackUiItem) -> PackUiItem) {
        _packsState.value = _packsState.value.map { if (it.pack.id == packId) transform(it) else it }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                // Zip-slip guard: entries must stay inside destDir.
                val out = File(destDir, entry.name).canonicalFile
                require(out.path.startsWith(destDir.canonicalPath + File.separator)) {
                    "Bad zip entry: ${entry.name}"
                }
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { ins -> out.outputStream().use { outs -> ins.copyTo(outs) } }
                }
            }
        }
    }
}
