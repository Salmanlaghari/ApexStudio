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
        val variable: Boolean = false
    )

    val fonts: List<BundledFont> = listOf(
        BundledFont("sans", "Default Sans", null),
        BundledFont("serif", "Serif", null),
        BundledFont("monospace", "Monospace", null),
        BundledFont("cursive", "Cursive", null),
        BundledFont(
            key = "poppins",
            label = "Poppins",
            regularAsset = "fonts/Poppins-Regular.ttf",
            boldAsset = "fonts/Poppins-Bold.ttf",
            italicAsset = "fonts/Poppins-Italic.ttf",
            boldItalicAsset = "fonts/Poppins-BoldItalic.ttf"
        ),
        BundledFont(
            key = "montserrat",
            label = "Montserrat",
            regularAsset = "fonts/Montserrat.ttf",
            variable = true
        ),
        BundledFont(
            key = "bebas",
            label = "Bebas Neue",
            regularAsset = "fonts/BebasNeue-Regular.ttf"
        ),
        BundledFont(
            key = "anton",
            label = "Anton",
            regularAsset = "fonts/Anton-Regular.ttf"
        ),
        BundledFont(
            key = "script",
            label = "Script",
            regularAsset = "fonts/GreatVibes-Regular.ttf"
        )
    )

    fun find(key: String?): BundledFont? =
        fonts.firstOrNull { it.key.equals(key, ignoreCase = true) }

    @Volatile
    private var appContext: Context? = null
    private val cache = mutableMapOf<String, Typeface>()
    private val lock = Any()

    fun init(context: Context) {
        appContext = context.applicationContext
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
