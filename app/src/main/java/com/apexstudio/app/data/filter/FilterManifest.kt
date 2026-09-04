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
}
