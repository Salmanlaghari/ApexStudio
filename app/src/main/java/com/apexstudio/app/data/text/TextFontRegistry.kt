package com.apexstudio.app.data.text

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.Log

/**
 * Bundled open-licensed (SIL OFL) fonts for the pro text tools.
 *
 * All TTFs live in `assets/fonts/` with their OFL.txt licence files
 * (sources: github.com/google/fonts). System families ("sans",
 * "serif", "monospace", "cursive") keep working for backwards
 * compatibility with older presets.
 *
 * Call [init] once with an application context (the editor screen
 * and the export engine both do); [resolve] falls back to system
 * fonts if init was never called so rendering can never crash.
 */
object TextFontRegistry {

    private const val TAG = "TextFontRegistry"

    data class BundledFont(
        val key: String,
        val label: String,
        /** Asset path of the regular face, or null for a system family. */
        val regularAsset: String?,
        val boldAsset: String? = null,
        val italicAsset: String? = null,
        val boldItalicAsset: String? = null,
        /** Set for variable fonts so real weight axes are used (API 28+). */
        val variable: Boolean = false,
        /** Picker grouping: System, Display, Handwriting, Serif, Sans Serif… */
        val category: String = "System"
    )

    private val coreFonts: List<BundledFont> = listOf(
        BundledFont("sans", "Default Sans", null, category = "System"),
        BundledFont("serif", "Serif", null, category = "System"),
        BundledFont("monospace", "Monospace", null, category = "System"),
        BundledFont("cursive", "Cursive", null, category = "System"),
        BundledFont(
            key = "poppins",
            label = "Poppins",
            regularAsset = "fonts/Poppins-Regular.ttf",
            category = "Sans Serif",
            boldAsset = "fonts/Poppins-Bold.ttf",
            italicAsset = "fonts/Poppins-Italic.ttf",
            boldItalicAsset = "fonts/Poppins-BoldItalic.ttf"
        ),
        BundledFont(
            key = "montserrat",
            label = "Montserrat",
            regularAsset = "fonts/Montserrat.ttf",
            variable = true,
            category = "Sans Serif"
        ),
        BundledFont(
            key = "bebas",
            label = "Bebas Neue",
            regularAsset = "fonts/BebasNeue-Regular.ttf",
            category = "Display"
        ),
        BundledFont(
            key = "anton",
            label = "Anton",
            regularAsset = "fonts/Anton-Regular.ttf",
            category = "Display"
        ),
        BundledFont(
            key = "script",
            label = "Script",
            regularAsset = "fonts/GreatVibes-Regular.ttf",
            category = "Handwriting"
        )
    )

    /**
     * Phase 4: full font catalogue — core fonts plus the 36 Google Fonts
     * (OFL) packs from `assets/fonts/fonts.json`, grouped by category for
     * the picker. Populated on [init]; before that, only core fonts.
     */
    val fonts: List<BundledFont>
        get() = coreFonts + manifestFonts

    @Volatile
    private var manifestFonts: List<BundledFont> = emptyList()

    fun find(key: String?): BundledFont? =
        fonts.firstOrNull { it.key.equals(key, ignoreCase = true) }

    @Volatile
    private var appContext: Context? = null
    private val cache = mutableMapOf<String, Typeface>()
    private val lock = Any()

    fun init(context: Context) {
        appContext = context.applicationContext
        if (manifestFonts.isEmpty()) {
            manifestFonts = loadManifestFonts(context.applicationContext)
        }
    }

    /** Reads `assets/fonts/fonts.json` (written by the Phase 4 font packer). */
    private fun loadManifestFonts(ctx: Context): List<BundledFont> {
        return try {
            val json = ctx.assets.open("fonts/fonts.json").bufferedReader().use { it.readText() }
            val out = mutableListOf<BundledFont>()
            val arr = org.json.JSONObject(json).optJSONArray("fonts") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val file = o.optString("file")
                if (file.isBlank()) continue
                val family = o.optString("family")
                if (family.isBlank()) continue
                val category = o.optString("category", "Display")
                val key = family.lowercase().replace("[^a-z0-9]+".toRegex(), "")
                out += BundledFont(
                    key = key,
                    label = family,
                    regularAsset = "fonts/$file",
                    variable = "-Variable." in file,
                    category = category
                )
            }
            Log.i(TAG, "Loaded ${out.size} manifest fonts")
            out
        } catch (e: Exception) {
            Log.w(TAG, "fonts.json not found — core fonts only", e)
            emptyList()
        }
    }

    private fun systemTypeface(key: String, bold: Boolean, italic: Boolean): Typeface {
        val base = when (key.lowercase()) {
            "serif" -> Typeface.SERIF
            "monospace", "mono" -> Typeface.MONOSPACE
            // No framework CURSIVE constant — the bundled "script"
            // font (Great Vibes) covers the script look.
            else -> Typeface.SANS_SERIF
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    private fun loadAsset(assetPath: String, bold: Boolean, italic: Boolean, variable: Boolean): Typeface? {
        val ctx = appContext ?: return null
        return try {
            if (variable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Real weight axis instead of synthetic emboldening.
                Typeface.Builder(ctx.assets, assetPath)
                    .setWeight(if (bold) 700 else 400)
                    .setItalic(italic)
                    .build()
            } else {
                val base = Typeface.createFromAsset(ctx.assets, assetPath)
                val style = when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                // create(base, NORMAL) returns base itself; any other
                // style synthesises bold/italic from the same outlines.
                Typeface.create(base, style)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bundled font $assetPath", e)
            null
        }
    }

    /**
     * Resolve [key] to a [Typeface], honouring bold/italic. Never
     * throws: falls back to a system family on any failure.
     */
    fun resolve(key: String?, bold: Boolean, italic: Boolean): Typeface {
        val entry = find(key)
        if (entry == null || entry.regularAsset == null) {
            return systemTypeface(key ?: "sans", bold, italic)
        }
        // Prefer dedicated bold/italic faces when the family ships them.
        val asset = when {
            bold && italic && entry.boldItalicAsset != null -> entry.boldItalicAsset
            bold && entry.boldAsset != null -> entry.boldAsset
            italic && entry.italicAsset != null -> entry.italicAsset
            else -> entry.regularAsset
        }
        val cacheKey = "$asset|$bold|$italic|${entry.variable}"
        synchronized(lock) {
            cache[cacheKey]?.let { return it }
        }
        val tf = loadAsset(asset, bold && entry.boldAsset == null, italic && entry.italicAsset == null, entry.variable)
            ?: systemTypeface("sans", bold, italic)
        synchronized(lock) {
            if (cache.size < 32) cache[cacheKey] = tf
        }
        return tf
    }
}
