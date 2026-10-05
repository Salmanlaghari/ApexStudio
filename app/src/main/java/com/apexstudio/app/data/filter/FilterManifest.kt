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
         * Published atomically via volatile reference swap; readers see either
         * the empty map or the fully-built unmodifiable map — never partial.
         * LinkedHashMap preserves deterministic insertion order for the gallery.
         */
        @Volatile
        private var staticRegistry: Map<String, FilterPreset> = emptyMap()

        /** Pre-compiled regex for case-insensitive `.cube` suffix stripping. */
        private val CUBE_SUFFIX = Regex("\\.cube$", RegexOption.IGNORE_CASE)

        /**
         * Scans `assets/luts/` for `.cube` files and builds the static registry.
         * Call once from Application.onCreate(). Thread-safe and idempotent.
         */
        @Synchronized
        fun initialize(context: android.content.Context) {
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
                    // Categorize: check specific temperature/tone tokens first,
                    // then broader families, so e.g. "film_bw_cool" -> Cool path
                    // is decided deliberately below.
                    val lowerId = id.lowercase()
                    val category = when {
                        "vintage" in lowerId -> "Vintage"
                        // Cold tones: check before "bw"/"film" so cool LUTs aren't swallowed
                        "cool" in lowerId || "cold" in lowerId ||
                        "glacier" in lowerId || "arctic" in lowerId ||
                        "frost" in lowerId || "winter" in lowerId ||
                        "midnight" in lowerId || "oslo" in lowerId -> "Cool"
                        "bw" in lowerId || "blackwhite" in lowerId ||
                        "mono" in lowerId || "noir" in lowerId -> "Black & White"
                        "cinematic" in lowerId || "film" in lowerId -> "Cinematic"
                        "warm" in lowerId -> "Warm"
                        else -> "General"
                    }
                    tempMap[id] = FilterPreset(
                        id = id,
                        name = name,
                        category = category,
                        asset = "luts/$file"
                    )
                }
                // Atomic publish: freeze as unmodifiable, swap the volatile ref
                staticRegistry = java.util.Collections.unmodifiableMap(tempMap)
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

        /**
         * Test-only: clears the registry so tests can start from a fresh scan.
         * Production code must never call this.
         */
        @Synchronized
        @androidx.annotation.VisibleForTesting
        fun resetForTesting() {
            staticRegistry = emptyMap()
        }
    }
}