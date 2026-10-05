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
         * Initialized via [initialize] — call from Application.onCreate().
         */
        private val staticRegistry = mutableMapOf<String, FilterPreset>()

        /**
         * Scans `assets/luts/` for `.cube` files and builds the static registry.
         * Call once from Application.onCreate(). Safe to call multiple times.
         */
        fun initialize(context: android.content.Context) {
            if (staticRegistry.isNotEmpty()) return
            try {
                val luts = context.assets.list("luts") ?: return
                for (file in luts) {
                    if (!file.endsWith(".cube", ignoreCase = true)) continue
                    val id = file.removeSuffix(".cube").removeSuffix(".CUBE")
                    val name = id.split("_", "-").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                    // Categorize by filename prefix (e.g. "vintage_xxx" → Vintage)
                    val category = when {
                        id.startsWith("vintage", ignoreCase = true) -> "Vintage"
                        id.startsWith("bw", ignoreCase = true) ||
                        id.startsWith("blackwhite", ignoreCase = true) ||
                        id.startsWith("mono", ignoreCase = true) -> "Black & White"
                        id.startsWith("cinematic", ignoreCase = true) -> "Cinematic"
                        id.startsWith("warm", ignoreCase = true) -> "Warm"
                        id.startsWith("cool", ignoreCase = true) -> "Cool"
                        else -> "General"
                    }
                    staticRegistry[id] = FilterPreset(
                        id = id,
                        name = name,
                        category = category,
                        asset = "luts/$file"
                    )
                }
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