package com.apexstudio.app.data.packs.download

import android.content.Context
import android.util.Log
import com.apexstudio.app.data.filter.GpuColorProfile
import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.data.packs.PackLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wires downloaded packs into the app's feature subsystems (PR-C).
 *
 * Called by [PackManager] right after a pack installs, and idempotently from
 * feature panels on open (covers packs downloaded while the app was running
 * or installed before a panel first opened).
 *
 * Currently supported:
 * - `filters` packs: `<packDir>/<entry>` (PackFilter JSON schema, same as the
 *   bundled `assets/packs/filters/filters.json`) → registered as GPU color
 *   profiles, so they appear in the filter panel with zero UI changes.
 *
 * Future categories (transitions / effects / models) get their own
 * `ensure…Registered` hooks here as their downloadables land.
 */
object DownloadedPacks {

    private const val TAG = "DownloadedPacks"

    /** Hook called by [PackManager] after a successful install. */
    suspend fun onPackInstalled(context: Context, packId: String) {
        val item = PackManager.packsState.value.find { it.pack.id == packId } ?: return
        if (item.pack.category == "filters") {
            ensureDownloadedFilterPacksRegistered(context)
        }
    }

    /**
     * Register filter profiles from every downloaded `filters` pack.
     * Idempotent — re-running only adds packs not yet registered.
     */
    suspend fun ensureDownloadedFilterPacksRegistered(context: Context) =
        withContext(Dispatchers.IO) {
            val statePacks = PackManager.packsState.value
                .filter { it.pack.category == "filters" && it.state == PackManager.PackState.Downloaded }
                .map { it.pack }
            val packs = if (statePacks.isNotEmpty()) {
                statePacks
            } else {
                // Fallback for callers before the first PackManager.refresh():
                // manifest entries (category-validated) intersected with the
                // on-disk install registry.
                val manifest = PackManifestRepo.fetchManifest(context)
                val installed = PackManager.installedPackIds(context)
                manifest.packs.filter { it.category == "filters" && it.id in installed }
            }
            registerFilterPacks(context, packs)
        }

    /**
     * Scan-based variant used when [PackManager.packsState] hasn't loaded yet
     * (e.g. very first panel open): finds installed pack dirs and validates
     * each against the manifest (id must exist, category must be `filters`)
     * so a stray/tampered directory can never inject content.
     */
    suspend fun ensureDownloadedFilterPacksRegisteredScan(context: Context) =
        withContext(Dispatchers.IO) {
            val manifest = PackManifestRepo.fetchManifest(context)
            val validIds = manifest.packs
                .filter { it.category == "filters" }
                .map { it.id }
                .toSet()
            val installed = PackManager.installedPackIds(context)
            val root = PackManager.packsRoot(context)
            val dirs = root.listFiles { f -> f.isDirectory } ?: return@withContext
            val packs = dirs.mapNotNull { dir ->
                val pack = manifest.packs.find { it.id == dir.name } ?: return@mapNotNull null
                if (pack.id !in validIds || pack.id !in installed) return@mapNotNull null
                pack
            }
            registerFilterPacks(context, packs)
        }

    private fun registerFilterPacks(
        context: Context,
        packs: List<PackManifestRepo.RemotePack>
    ) {
        val allProfiles = mutableListOf<GpuColorProfile>()
        for (pack in packs) {
            val entryFile = File(PackManager.packDir(context, pack.id), pack.entry)
            if (!entryFile.exists()) {
                Log.w(TAG, "Pack entry missing: ${pack.id}/${pack.entry}")
                continue
            }
            val filters = PackLoader.loadFiltersFromFile(entryFile)
            if (filters.isEmpty()) {
                Log.w(TAG, "No filters parsed from pack ${pack.id}")
                continue
            }
            // Namespaced per pack id — no assumption about the filter ids'
            // own prefixes, and two packs can never collide.
            val profiles = PackLoader.toColorProfiles(filters).map { profile ->
                profile.copy(id = "dl_${pack.id}_${profile.id}")
            }
            allProfiles += profiles
            Log.i(TAG, "Downloaded pack '${pack.id}': ${profiles.size} filters")
        }
        if (allProfiles.isNotEmpty()) {
            GpuColorProfiles.registerDownloadedPackProfiles(allProfiles)
        }
    }
}
