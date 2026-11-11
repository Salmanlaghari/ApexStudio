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
            (downloadedRegistry.keys + next.keys)
                .filter { downloadedRegistry[it] != next[it] }
                .forEach { LutBitmapCache.evict(it) }
            downloadedRegistry = java.util.Collections.unmodifiableMap(next)
            _downloadedRegistryVersion.value = _downloadedRegistryVersion.value + 1
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
