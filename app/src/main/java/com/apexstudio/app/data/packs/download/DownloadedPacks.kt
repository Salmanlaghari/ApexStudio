package com.apexstudio.app.data.packs.download

import android.content.Context
import android.util.Log
import com.apexstudio.app.data.fx.DownloadedEffectRepository
import com.apexstudio.app.data.filter.DownloadedLutFilterRepository
import com.apexstudio.app.data.filter.GpuColorProfile
import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.data.packs.PackLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Wires verified downloadable packs into the app's feature subsystems. */
object DownloadedPacks {

    private const val TAG = "DownloadedPacks"

    /** Called after the install registry is committed, before the UI state flips to Downloaded. */
    suspend fun onPackInstalled(context: Context, packId: String): Boolean {
        val manifest = PackManifestRepo.fetchManifest(context)
        val pack = manifest.packs.firstOrNull { it.id == packId } ?: return false
        return when (pack.category) {
            "filters" -> {
                ensureDownloadedFilterPacksRegisteredScan(context, manifest)
                true
            }
            "effects" -> {
                DownloadedEffectRepository.refreshInstalled(context, manifest)
                DownloadedEffectRepository.hasPack(packId)
            }
            else -> true
        }
    }

    /** Register every currently installed filter payload; safe to call on editor open. */
    suspend fun ensureDownloadedFilterPacksRegistered(context: Context) =
        ensureDownloadedFilterPacksRegisteredScan(context)

    /** Register every currently installed, checksum-verified FX profile pack. */
    suspend fun ensureDownloadedEffectPacksRegistered(
        context: Context,
        suppliedManifest: PackManifestRepo.PackManifestFile? = null
    ) {
        DownloadedEffectRepository.refreshInstalled(context, suppliedManifest)
    }

    /**
     * Finds installed packs, validates their catalog category and payload type,
     * preserves legacy parametric-profile registration, and atomically replaces
     * the downloaded LUT catalog so deleted or damaged files stop resolving.
     */
    suspend fun ensureDownloadedFilterPacksRegisteredScan(
        context: Context,
        suppliedManifest: PackManifestRepo.PackManifestFile? = null
    ) = withContext(Dispatchers.IO) {
        val manifest = suppliedManifest ?: PackManifestRepo.fetchManifest(context)
        val installed = PackManager.installedPackIds(context)
        val root = PackManager.packsRoot(context)
        val dirs = root.listFiles { f -> f.isDirectory }?.map { it.name }?.toSet().orEmpty()
        val packs = manifest.packs.filter { pack ->
            pack.category == "filters" && pack.id in installed && pack.id in dirs
        }
        val profilePacks = packs.filter { it.payloadType == PackManifestRepo.FILTER_PROFILES_PAYLOAD }
        registerFilterPacks(context, profilePacks)
        DownloadedLutFilterRepository.refreshInstalled(context, manifest)
    }

    private fun registerFilterPacks(
        context: Context,
        packs: List<PackManifestRepo.RemotePack>
    ) {
        val allProfiles = mutableListOf<GpuColorProfile>()
        for (pack in packs) {
            val entryFile = safeEntryFile(PackManager.packDir(context, pack.id), pack.entry)
            if (entryFile == null || !entryFile.isFile) {
                Log.w(TAG, "Pack entry missing or unsafe: ${pack.id}/${pack.entry}")
                continue
            }
            val filters = PackLoader.loadFiltersFromFile(entryFile)
            if (filters.isEmpty()) {
                Log.w(TAG, "No filters parsed from pack ${pack.id}")
                continue
            }
            val profiles = PackLoader.toColorProfiles(filters).map { profile ->
                profile.copy(id = "dl_${pack.id}_${profile.id}")
            }
            allProfiles += profiles
            Log.i(TAG, "Downloaded profile pack '${pack.id}': ${profiles.size} filters")
        }
        GpuColorProfiles.replaceDownloadedPackProfiles(allProfiles)
    }

    private fun safeEntryFile(root: File, relative: String): File? {
        if (relative.isBlank() || relative.startsWith('/') || '\\' in relative) return null
        if (relative.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        val base = root.canonicalFile
        val entry = File(base, relative).canonicalFile
        return entry.takeIf { it.path.startsWith(base.path + File.separator) }
    }
}
