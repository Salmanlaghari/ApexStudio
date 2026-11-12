package com.apexstudio.app.data.filter

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One entry in the LUT catalog. Bundled [asset] paths are relative to `assets/`;
 * downloaded presets use a verified `downloaded-lut://` URI and include pinned
 * file metadata so preview and export can recheck integrity at load time.
 */
data class FilterPreset(
    val id: String,
    val name: String,
    val category: String,
    val asset: String,
    val assetSha256: String? = null,
    val assetSizeBytes: Long? = null
)

data class FilterCategory(
    val id: String,
    val name: String,
    val filters: List<FilterPreset>
)

data class FilterManifest(
    val categories: List<FilterCategory>,
    val filters: List<FilterPreset>
) {
    fun categoryById(id: String): FilterCategory? = categories.firstOrNull { it.id == id }
    fun presetById(id: String): FilterPreset? = filters.firstOrNull { it.id == id }
    fun presetsInCategory(categoryId: String): List<FilterPreset> {
        val cat = categoryById(categoryId) ?: return emptyList()
        return filters.filter { it.category.equals(cat.name, ignoreCase = true) }
    }

    companion object {
        /** Bundled LUTs scanned from `assets/luts/` at application startup. */
        @Volatile
        private var staticRegistry: Map<String, FilterPreset> = emptyMap()

        /** Verified, installed on-demand LUT files, replaced atomically after each scan. */
        @Volatile
        private var downloadedRegistry: Map<String, FilterPreset> = emptyMap()

        private val _downloadedRegistryVersion = MutableStateFlow(0)
        val downloadedRegistryVersion: StateFlow<Int> = _downloadedRegistryVersion.asStateFlow()

        private val CUBE_SUFFIX = Regex("\\.cube$", RegexOption.IGNORE_CASE)

        /** Scans bundled LUT assets. Thread-safe and idempotent. */
        @Synchronized
        fun initialize(context: Context) {
            if (staticRegistry.isNotEmpty()) return
            try {
                val luts = context.assets.list("luts") ?: return
                val tempMap = LinkedHashMap<String, FilterPreset>()
                for (file in luts) {
                    if (!file.endsWith(".cube", ignoreCase = true)) continue
                    val id = file.replace(CUBE_SUFFIX, "")
                    val name = id.split("_", "-", ".").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                    val lowerId = id.lowercase()
                    val category = when {
                        lowerId.startsWith("creator_") -> "Creator Essentials"
                        "vintage" in lowerId -> "Vintage"
                        "cool" in lowerId || "cold" in lowerId || "glacier" in lowerId ||
                            "arctic" in lowerId || "frost" in lowerId || "winter" in lowerId ||
                            "midnight" in lowerId || "oslo" in lowerId -> "Cool"
                        "bw" in lowerId || "blackwhite" in lowerId || "mono" in lowerId || "noir" in lowerId -> "Black & White"
                        "cinematic" in lowerId || "film" in lowerId -> "Cinematic"
                        "warm" in lowerId -> "Warm"
                        else -> "General"
                    }
                    tempMap[id] = FilterPreset(id = id, name = name, category = category, asset = "luts/$file")
                }
                staticRegistry = java.util.Collections.unmodifiableMap(tempMap)
                android.util.Log.i("FilterManifest", "Scanned ${staticRegistry.size} LUT presets from assets")
            } catch (e: Exception) {
                android.util.Log.w("FilterManifest", "LUT scan failed", e)
            }
        }

        /**
         * True when two presets for the same id point at different LUT
         * content. The comparison is explicit, field by field, over exactly
         * the fields that determine the cached pixels — [FilterPreset.asset],
         * [FilterPreset.assetSha256] and [FilterPreset.assetSizeBytes] —
         * instead of relying on data-class `equals` over every field
         * (including display-only name/category and nullable metadata, where
         * a null-vs-value difference is easy to misread).
         * (Fix-forward for PR #231, Kilo CRITICAL 3.)
         */
        @VisibleForTesting
        fun lutContentChanged(old: FilterPreset?, new: FilterPreset?): Boolean {
            if (old == null || new == null) return old !== new
            return old.asset != new.asset ||
                !old.assetSha256.equals(new.assetSha256, ignoreCase = true) ||
                old.assetSizeBytes != new.assetSizeBytes
        }

        /** Atomically replaces downloaded presets; invalid/removed packs disappear from future lookups. */
        @Synchronized
        fun replaceDownloadedPresets(presets: List<FilterPreset>) {
            val next = LinkedHashMap<String, FilterPreset>()
            presets.forEach { preset ->
                if (preset.id.isNotBlank() && preset.id !in staticRegistry && preset.id !in next) {
                    next[preset.id] = preset
                }
            }
            if (downloadedRegistry == next && downloadedRegistry.keys.toList() == next.keys.toList()) return
            // Work out which ids need cache eviction BEFORE the swap, using
            // the explicit content comparison above: removed ids, newly
            // added ids, and ids whose LUT content (asset/sha/size) changed.
            val evictions = (downloadedRegistry.keys + next.keys)
                .filter { id -> lutContentChanged(downloadedRegistry[id], next[id]) }
            // Eviction ordering matters (Kilo CRITICAL 3): swap the registry
            // and bump the version FIRST, then evict. Evicting before the
            // swap left a window where a concurrent reader could re-load the
            // OLD preset from the still-live old registry and repopulate the
            // cache after the eviction, leaving stale pixels cached under
            // the new preset. After the swap, any re-load resolves the new
            // preset and its fingerprint no longer matches the stale entry.
            downloadedRegistry = java.util.Collections.unmodifiableMap(next)
            _downloadedRegistryVersion.value = _downloadedRegistryVersion.value + 1
            evictions.forEach { LutBitmapCache.evict(it) }
        }

        fun downloadedPresets(): List<FilterPreset> = downloadedRegistry.values.toList()

        /** Merge installed LUTs into a bundled manifest while preserving category and item order. */
        fun withDownloadedPresets(base: FilterManifest): FilterManifest {
            val downloaded = downloadedRegistry.values.toList()
            if (downloaded.isEmpty()) return base
            val mergedFilters = base.filters + downloaded.filter { extra -> base.filters.none { it.id == extra.id } }
            val categoryDefs = LinkedHashMap<String, Pair<String, String>>()
            base.categories.forEach { category ->
                categoryDefs.putIfAbsent(category.name.lowercase(), category.id to category.name)
            }
            downloaded.map { it.category }.distinctBy { it.lowercase() }.forEach { name ->
                val key = name.lowercase()
                if (key !in categoryDefs) {
                    val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "other" }
                    categoryDefs[key] = "downloaded_$slug" to name
                }
            }
            val mergedCategories = categoryDefs.values.map { (id, name) ->
                FilterCategory(
                    id = id,
                    name = name,
                    filters = mergedFilters.filter { it.category.equals(name, ignoreCase = true) }
                )
            }.filter { it.filters.isNotEmpty() }
            return FilterManifest(categories = mergedCategories, filters = mergedFilters)
        }

        /** Lookup for built-in templates and saved editor projects. */
        fun presetById(id: String): FilterPreset? = downloadedRegistry[id] ?: staticRegistry[id]

        @Synchronized
        @VisibleForTesting
        fun resetForTesting() {
            staticRegistry = emptyMap()
            downloadedRegistry = emptyMap()
            LutBitmapCache.clear()
            _downloadedRegistryVersion.value = _downloadedRegistryVersion.value + 1
        }
    }
}
