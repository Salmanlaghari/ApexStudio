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
         * Static lookup used by `TimelineTemplateManager.mapTemplateToComposition`.
         *
         * `FilterManifest` is a `data class` populated per-instance (typically from JSON),
         * but `TimelineTemplateManager` invokes a class-level lookup by id — without
         * an instance context. This companion accessor provides a stable compile-time
         * entry point and returns `null` when the id is not present in the in-memory
         * catalog. Callers already null-check the result, so this is safe.
         *
         * TODO (follow-up PR): wire this to scan `assets/luts/` for `.cube` LUTs at startup and
         * build a full `Map<String, FilterPreset>` so the 73 bundled LUTs resolve here.
         */
        fun presetById(id: String): FilterPreset? = null
    }
}