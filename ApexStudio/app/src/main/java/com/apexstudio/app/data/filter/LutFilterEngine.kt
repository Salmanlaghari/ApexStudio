package com.apexstudio.app.data.filter

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses a .cube 3D LUT file into a flat float[] of size size*size*size*3
 * (R, G, B triples, ordered with B changing slowest, then G, then R —
 * which matches the convention every GL shader for lookup tables uses).
 */
object CubeLutParser {
    private const val TAG = "CubeLutParser"

    fun parse(reader: BufferedReader): FloatArray? {
        var size = 0
        val data = ArrayList<Float>(3 * 17 * 17 * 17)
        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("TITLE") || trimmed.startsWith("DOMAIN")) {
                    continue
                }
                if (trimmed.startsWith("LUT_3D_SIZE")) {
                    size = trimmed.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: 0
                    continue
                }
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val r = parts[0].toFloatOrNull()
                    val g = parts[1].toFloatOrNull()
                    val b = parts[2].toFloatOrNull()
                    if (r != null && g != null && b != null) {
                        data.add(r)
                        data.add(g)
                        data.add(b)
                    }
                }
            }
        }
        if (size == 0 || data.size != size * size * size * 3) {
            Log.w(TAG, "Invalid .cube: size=$size, entries=${data.size / 3}")
            return null
        }
        return data.toFloatArray()
    }
}

/**
 * Loads the bundled filter manifest + .cube LUT assets and exposes
 * the parsed 3D LUTs to the GL filter pipeline.
 */
class LutFilterEngine(private val context: Context) {

    val manifest: FilterManifest by lazy { loadManifest() }

    /**
     * Read the .cube file for [preset] and return its 3D LUT as a
     * flat float[]. Returns null if the asset is missing or malformed.
     */
    fun loadLut(preset: FilterPreset): FloatArray? {
        return try {
            context.assets.open(preset.asset).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    CubeLutParser.parse(reader)
                }
            }
        } catch (e: Exception) {
            Log.w("LutFilterEngine", "Failed to load LUT ${preset.asset}", e)
            null
        }
    }

    private fun loadManifest(): FilterManifest {
        val text = context.assets.open("luts/filter_manifest.json").use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
        val json = JSONObject(text)
        val catArr = json.getJSONArray("categories")
        val filterArr = json.getJSONArray("filters")
        val filters = ArrayList<FilterPreset>(filterArr.length())
        for (i in 0 until filterArr.length()) {
            val o = filterArr.getJSONObject(i)
            filters.add(
                FilterPreset(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    category = o.getString("category"),
                    asset = o.getString("asset")
                )
            )
        }
        val categories = ArrayList<FilterCategory>(catArr.length())
        for (i in 0 until catArr.length()) {
            val o = catArr.getJSONObject(i)
            val ids = o.getJSONArray("filters")
            val catName = o.getString("name")
            val inCat = ArrayList<FilterPreset>(ids.length())
            for (j in 0 until ids.length()) {
                val fid = ids.getString(j)
                inCat.add(filters.first { it.id == fid })
            }
            categories.add(FilterCategory(id = o.getString("id"), name = catName, filters = inCat))
        }
        return FilterManifest(categories = categories, filters = filters)
    }
}
