package com.apexstudio.app.ui.screens.editor

import android.content.Context
import com.apexstudio.app.data.fx.DownloadedFxEffect
import com.apexstudio.app.data.fx.FxPreset
import org.json.JSONArray
import java.util.Locale

internal data class FxSearchResult(
    val id: String,
    val label: String,
    val category: String,
    val downloaded: Boolean,
    val family: String? = null,
)

/**
 * Search index for effects that already exist in the real preview/export pipeline.
 * Downloaded results are included only after the existing verified-pack loader supplies them.
 */
internal object FxSearchCatalog {
    const val RECENT_LIMIT = 8
    private const val PREFERENCES_NAME = "fx_search_history_v1"
    private const val PREFERENCES_KEY = "recent_queries"

    // Curated discovery terms are limited to effects (or aliases) that resolve to real catalog entries.
    val suggestedQueries = listOf(
        "oblique blur", "flash", "soft", "glitch", "black", "halo blur", "glow", "god rays"
    )

    private val aliasesById = mapOf(
        "tilt_shift" to listOf("oblique blur", "soft blur"),
        "radial_blur" to listOf("oblique blur"),
        "strobe" to listOf("flash", "light flash"),
        "lightning_aura" to listOf("flash", "lightning"),
        "crash_impact" to listOf("explosion", "impact", "burst"),
        "fire_ember" to listOf("explosion", "fire", "flame"),
        "film_burn" to listOf("explosion", "burn", "flare"),
        "black_hole_warp" to listOf("black", "vortex", "swirl"),
        "halation" to listOf("halo blur", "halo", "soft glow"),
        "soft_blur" to listOf("halo blur", "soft"),
        "glow_diffuse" to listOf("halo blur", "glow"),
        "sun_beam" to listOf("god rays", "godrays", "sunlight"),
        "camera_wobble" to listOf("camera shake", "handheld"),
        "cherry_blossom" to listOf("blossom", "petals"),
    )

    fun search(query: String, downloadedEffects: List<DownloadedFxEffect>): List<FxSearchResult> {
        val tokens = normalize(query).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) return emptyList()

        val builtIns = FxPreset.values().map { preset ->
            FxSearchResult(
                id = preset.id,
                label = preset.label,
                category = preset.category,
                downloaded = false,
            )
        }
        val builtInIds = builtIns.mapTo(mutableSetOf()) { it.id }
        val downloaded = downloadedEffects
            .asSequence()
            .filter { it.id !in builtInIds }
            .distinctBy { it.id }
            .map { effect ->
                FxSearchResult(
                    id = effect.id,
                    label = effect.label,
                    category = effect.category,
                    downloaded = true,
                    family = effect.family,
                )
            }
            .toList()

        return (builtIns + downloaded).filter { result ->
            val fields = buildList {
                add(result.label)
                add(result.id)
                add(result.category)
                result.family?.let(::add)
                aliasesById[result.id]?.let(::addAll)
            }
            val searchable = normalize(fields.joinToString(" "))
            tokens.all(searchable::contains)
        }
    }

    fun addRecent(current: List<String>, rawQuery: String): List<String> {
        val query = rawQuery.trim().replace(Regex("\\s+"), " ")
        if (query.isBlank()) return sanitizeRecent(current)
        return (listOf(query) + sanitizeRecent(current).filterNot { it.equals(query, ignoreCase = true) })
            .take(RECENT_LIMIT)
    }

    fun sanitizeRecent(queries: Iterable<String>): List<String> {
        val seen = mutableSetOf<String>()
        return queries.asSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter(String::isNotBlank)
            .filter { seen.add(it.lowercase(Locale.ROOT)) }
            .take(RECENT_LIMIT)
            .toList()
    }

    fun loadRecent(context: Context): List<String> = runCatching {
        val raw = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PREFERENCES_KEY, null)
            ?: return emptyList()
        val values = JSONArray(raw)
        sanitizeRecent((0 until values.length()).mapNotNull { index ->
            values.optString(index).takeIf(String::isNotBlank)
        })
    }.getOrDefault(emptyList())

    fun saveRecent(context: Context, queries: List<String>) {
        runCatching {
            val json = JSONArray().apply { sanitizeRecent(queries).forEach(::put) }.toString()
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREFERENCES_KEY, json)
                .apply()
        }
    }

    fun clearRecent(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFERENCES_KEY)
            .apply()
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
}
