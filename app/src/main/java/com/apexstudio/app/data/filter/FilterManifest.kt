package com.apexstudio.app.data.filter

/**
 * One entry in the 70+ LUT catalog. The [asset] path is relative to
 * `assets/` and is loaded by [LutFilterEngine] into a 3D GL texture.
 */
data class FilterPreset(
    val id: String,
    val name: String,
    val category: String,
    val asset: String
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
        // Preserve manifest order
        return filters.filter { it.category == cat.name }
    }

    companion object {
        /**
         * Static registry of LUT presets scanned from `assets/luts/` at startup.
         * Thread-safe: ConcurrentHashMap for safe read/write across threads.
         * Initialized via [initialize] — call from Application.onCreate().
         */
        private val staticRegistry = java.util.concurrent.ConcurrentHashMap<String, FilterPreset>()
        @Volatile
        private var initialized = false

        /**
         * Scans `assets/luts/` for `.cube` files and builds the static registry.
         * Call once from Application.onCreate(). Thread-safe and idempotent.
         */
        @Synchronized
        fun initialize(context: android.content.Context) {
            if (initialized) return
            try {
                val luts = context.assets.list("luts") ?: return
                val tempMap = mutableMapOf<String, FilterPreset>()
                for (file in luts) {
                    if (!file.endsWith(".cube", ignoreCase = true)) continue
                    // Case-insensitive suffix removal (Kilo: removeSuffix is case-sensitive)
                    val id = file.replace(Regex("\\.cube$", RegexOption.IGNORE_CASE), "")
                    val name = id.split("_", "-", ".").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                    // Categorize by checking if genre token appears anywhere in id
                    val lowerId = id.lowercase()
                    val category = when {
                        "vintage" in lowerId -> "Vintage"
                        "bw" in lowerId || "blackwhite" in lowerId ||
                        "mono" in lowerId || "noir" in lowerId -> "Black & White"
                        "cinematic" in lowerId || "film" in lowerId -> "Cinematic"
                        "warm" in lowerId -> "Warm"
                        "cool" in lowerId -> "Cool"
                        else -> "General"
                    }
                    tempMap[id] = FilterPreset(
                        id = id,
                        name = name,
                        category = category,
                        asset = "luts/$file"
                    )
                }
                // Atomic publish: only mark initialized after full successful scan
                staticRegistry.putAll(tempMap)
                initialized = true
                android.util.Log.i("FilterManifest", "Scanned ${staticRegistry.size} LUT presets from assets")
            } catch (e: Exception) {
                android.util.Log.w("FilterManifest", "LUT scan failed", e)
            }
        }

        /**
         * Static lookup used by `TimelineTemplateManager.mapTemplateToComposition`.
         *
         * Returns the preset from the static registry (populated by [initialize]),
         * or null if not found. Callers already null-check the result.
         */
        fun presetById(id: String): FilterPreset? = staticRegistry[id]
    }
}