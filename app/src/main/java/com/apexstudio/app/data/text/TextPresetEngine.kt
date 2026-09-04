package com.apexstudio.app.data.text

import com.apexstudio.app.domain.model.TextOverlay

data class TextPreset(
    val id: String,
    val name: String,
    val category: String,
    val colorArgb: Long,
    val bgArgb: Long? = null,
    val strokeColorArgb: Long? = null,
    val shadowColorArgb: Long? = null,
    val fontFamily: String = "sans",
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val sizeScale: Float = 1f
) {
    fun applyTo(overlay: TextOverlay): TextOverlay = overlay.copy(
        presetId = id,
        colorArgb = colorArgb,
        bgArgb = bgArgb,
        strokeColorArgb = strokeColorArgb,
        shadowColorArgb = shadowColorArgb,
        fontFamily = fontFamily,
        isBold = isBold,
        isItalic = isItalic,
        sizeScale = sizeScale
    )
}

/**
 * 500+ Text Studio Preset Engine for ApexStudio.
 * Provides over 500 stylized typography presets covering Cinematic, Cyberpunk,
 * Social/Vlog, Minimalist, Broadcast, Retro 80s, Gaming, Luxury, Comic Pop, and Tech Glitch.
 */
object TextPresetEngine {

    val categories = listOf(
        "Cinematic",
        "Cyberpunk",
        "Social / Vlog",
        "Minimalist",
        "Broadcast",
        "Retro 80s",
        "Gaming",
        "Luxury",
        "Comic Pop",
        "Tech & Glitch"
    )

    val presets: List<TextPreset> by lazy {
        val list = mutableListOf<TextPreset>()

        // 1. Cinematic Category (55 presets)
        val cinematicTints = listOf(
            Triple("Gold Foil", 0xFFFFD700L, 0xCC1A1400L),
            Triple("Silver Screen", 0xFFE0E0E0L, 0xCC101010L),
            Triple("Epic Serif", 0xFFFFFFFFL, 0xCC000000L),
            Triple("Film Noir", 0xFFD8D8D8L, 0xDD0A0A0AL),
            Triple("Blade Titanium", 0xFFB0C4DEL, 0xCC051020L),
            Triple("Amber Horizon", 0xFFFFB300L, 0xCC200800L),
            Triple("Director Slate", 0xFFFFFFFFL, 0xEE222222L),
            Triple("Dune Bronze", 0xFFCD7F32L, 0xCC1A0C00L),
            Triple("Oppenheimer Flame", 0xFFFF7043L, 0xCC1B0500L),
            Triple("Interstellar Cold", 0xFF81D4FAL, 0xCC001020L),
            Triple("Dark Knight Steel", 0xFF90A4AEL, 0xDD0D1117L)
        )
        for (i in 0 until 5) {
            cinematicTints.forEachIndexed { idx, (name, fg, bg) ->
                val id = "cine_${i}_$idx"
                val variant = when (i) {
                    0 -> "Classic"
                    1 -> "Wide Letterbox"
                    2 -> "Dramatic Italic"
                    3 -> "Heavy Title"
                    else -> "Subtle Monolith"
                }
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name $variant",
                        category = "Cinematic",
                        colorArgb = fg,
                        bgArgb = if (i % 2 == 1) bg else null,
                        strokeColorArgb = if (i == 3) 0xFF000000L else null,
                        shadowColorArgb = if (i != 1) 0xCC000000L else null,
                        fontFamily = if (i == 0 || i == 2) "serif" else "sans",
                        isBold = i != 4,
                        isItalic = i == 2,
                        sizeScale = if (i == 3) 1.25f else if (i == 4) 0.85f else 1.0f
                    )
                )
            }
        }

        // 2. Cyberpunk Category (55 presets)
        val cyberColors = listOf(
            Pair("Neon Cyan", 0xFF00E5FFL),
            Pair("Acid Yellow", 0xFFFFEA00L),
            Pair("Hot Magenta", 0xFFFF007FL),
            Pair("Matrix Green", 0xFF00FF66L),
            Pair("Toxic Violet", 0xFFAA00FFL),
            Pair("Plasma Blue", 0xFF2979FFL),
            Pair("Laser Orange", 0xFFFF6D00L),
            Pair("Hyper Crimson", 0xFFFF1744L),
            Pair("Electric Mint", 0xFF1DE9B6L),
            Pair("Night City Gold", 0xFFFFD600L),
            Pair("Deep UV", 0xFF651FFFL)
        )
        for (i in 0 until 5) {
            cyberColors.forEachIndexed { idx, (name, col) ->
                val id = "cyber_${i}_$idx"
                val styleName = when (i) {
                    0 -> "Glow Pulse"
                    1 -> "Dark Pill"
                    2 -> "Holo Wire"
                    3 -> "Overdrive"
                    else -> "Monospace Glitch"
                }
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name $styleName",
                        category = "Cyberpunk",
                        colorArgb = col,
                        bgArgb = if (i == 1) 0xDD050811L else if (i == 3) 0xBB000000L else null,
                        strokeColorArgb = if (i == 2 || i == 3) 0xFF050B14L else null,
                        shadowColorArgb = col,
                        fontFamily = if (i == 4) "monospace" else "sans",
                        isBold = true,
                        isItalic = i == 3,
                        sizeScale = 1.05f
                    )
                )
            }
        }

        // 3. Social / Vlog Category (55 presets)
        val socialThemes = listOf(
            Triple("TikTok Yellow", 0xFF000000L, 0xFFFFE600L),
            Triple("TikTok White", 0xFFFFFFFFL, 0xCC000000L),
            Triple("YouTube Red Tag", 0xFFFFFFFFL, 0xFFFF0000L),
            Triple("Instagram Purple", 0xFFFFFFFFL, 0xFFC13584L),
            Triple("Snap Yellow", 0xFF000000L, 0xFFFFFC00L),
            Triple("Vlog Caption Pill", 0xFFFFFFFFL, 0xAA000000L),
            Triple("Viral Highlighter", 0xFF111111L, 0xFF00F5D4L),
            Triple("Podcast Clean", 0xFFF8F9FAL, 0xCC1A1A24L),
            Triple("Shorts Bold", 0xFFFFFFFFL, 0xFFFF0055L),
            Triple("Reels Minimal", 0xFFFFFFFFL, 0x88000000L),
            Triple("Meme Impact", 0xFFFFFFFFL, null)
        )
        for (i in 0 until 5) {
            socialThemes.forEachIndexed { idx, (name, fg, bg) ->
                val id = "soc_${i}_$idx"
                val suffix = when (i) {
                    0 -> "Primary"
                    1 -> "Compact"
                    2 -> "Outlined"
                    3 -> "Dynamic"
                    else -> "Punchy"
                }
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name $suffix",
                        category = "Social / Vlog",
                        colorArgb = fg,
                        bgArgb = bg,
                        strokeColorArgb = if (i == 2 || name.contains("Impact")) 0xFF000000L else null,
                        shadowColorArgb = if (bg == null) 0xAA000000L else null,
                        fontFamily = "sans",
                        isBold = true,
                        isItalic = i == 3,
                        sizeScale = if (i == 1) 0.85f else if (i == 4) 1.2f else 1.0f
                    )
                )
            }
        }

        // 4. Minimalist Category (50 presets)
        val minimalAccents = listOf(
            Pair("Pure White", 0xFFFFFFFFL),
            Pair("Muted Grey", 0xFFB0B0B0L),
            Pair("Soft Cream", 0xFFF5F5DCL),
            Pair("Warm Ivory", 0xFFFFFFF0L),
            Pair("Charcoal Ink", 0xFF222222L),
            Pair("Oatmeal", 0xFFE0D8C8L),
            Pair("Nordic Slate", 0xFF78909CL),
            Pair("Blush Neutral", 0xFFE2C4C4L),
            Pair("Sage Calm", 0xFFA3B899L),
            Pair("Monochrome Ghost", 0xCCFFFFFFL)
        )
        for (i in 0 until 5) {
            minimalAccents.forEachIndexed { idx, (name, col) ->
                val id = "min_${i}_$idx"
                val font = if (i % 2 == 0) "sans" else "serif"
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name (v${i + 1})",
                        category = "Minimalist",
                        colorArgb = col,
                        bgArgb = if (i == 3) 0x40000000L else null,
                        strokeColorArgb = null,
                        shadowColorArgb = if (i == 1) 0x60000000L else null,
                        fontFamily = font,
                        isBold = i == 0,
                        isItalic = i == 2,
                        sizeScale = if (i == 4) 0.8f else 0.95f
                    )
                )
            }
        }

        // 5. Broadcast Category (50 presets)
        val newsThemes = listOf(
            Triple("Breaking Red", 0xFFFFFFFFL, 0xFFD32F2FL),
            Triple("Lower Third Blue", 0xFFFFFFFFL, 0xFF1976D2L),
            Triple("Sports Orange", 0xFFFFFFFFL, 0xFFE65100L),
            Triple("Financial Gold", 0xFF000000L, 0xFFFFD700L),
            Triple("Live Broadcast", 0xFFFFFFFFL, 0xFFC2185BL),
            Triple("Weather Amber", 0xFFFFFFFFL, 0xFFFFA000L),
            Triple("Special Report", 0xFFFFFFFFL, 0xFF303F9FL),
            Triple("Documentary Slate", 0xFFFFFFFFL, 0xFF37474FL),
            Triple("Interview Clean", 0xFFFFFFFFL, 0xCC111111L),
            Triple("Ticker Dark", 0xFF00E5FFL, 0xEE0A0E1AL)
        )
        for (i in 0 until 5) {
            newsThemes.forEachIndexed { idx, (name, fg, bg) ->
                val id = "broad_${i}_$idx"
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name Stage ${i + 1}",
                        category = "Broadcast",
                        colorArgb = fg,
                        bgArgb = bg,
                        strokeColorArgb = if (i == 2) 0xFF000000L else null,
                        shadowColorArgb = 0x88000000L,
                        fontFamily = "sans",
                        isBold = true,
                        isItalic = i == 4,
                        sizeScale = if (i == 0) 1.1f else 0.9f
                    )
                )
            }
        }

        // 6. Retro 80s Category (50 presets)
        val retroTints = listOf(
            Triple("Synthwave Pink", 0xFFFF007FL, 0xDD1B0033L),
            Triple("Arcade Yellow", 0xFFFFE600L, 0xFF000000L),
            Triple("Miami Vice Cyan", 0xFF00F5D4L, 0xDD7B2CBFL),
            Triple("VHS Timestamp", 0xFF00FF00L, 0xCC000000L),
            Triple("Cassette Orange", 0xFFFF5722L, 0xCC212121L),
            Triple("Chrome Metallic", 0xFFE0F7FAL, 0xDD002538L),
            Triple("Outrun Sunset", 0xFFFF758CL, 0xCC2E0854L),
            Triple("Laser Tag", 0xFF76FF03L, 0xEE050510L),
            Triple("Pixel CRT", 0xFF64FFDAL, 0xEE021008L),
            Triple("Radical Magenta", 0xFFFF4081L, null)
        )
        for (i in 0 until 5) {
            retroTints.forEachIndexed { idx, (name, fg, bg) ->
                val id = "retro_${i}_$idx"
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name '8${i + 5}",
                        category = "Retro 80s",
                        colorArgb = fg,
                        bgArgb = bg,
                        strokeColorArgb = if (i == 1) 0xFF000000L else null,
                        shadowColorArgb = fg,
                        fontFamily = if (i % 2 == 1) "monospace" else "sans",
                        isBold = true,
                        isItalic = i == 3,
                        sizeScale = 1.0f
                    )
                )
            }
        }

        // 7. Gaming & Esports (50 presets)
        val gamingColors = listOf(
            Triple("Victory Gold", 0xFFFFD700L, 0xEE120A00L),
            Triple("Defeat Crimson", 0xFFFF1744L, 0xEE1B0000L),
            Triple("Energy Cyan", 0xFF00E5FFL, 0xEE001B24L),
            Triple("Overclock Purple", 0xFFD500F9L, 0xEE140024L),
            Triple("Tactical Green", 0xFF00E676L, 0xEE001B0AL),
            Triple("Respawn White", 0xFFFFFFFFL, 0xEE000000L),
            Triple("Stealth Dark", 0xFF9E9E9EL, 0xEE111111L),
            Triple("Killstreak Red", 0xFFFF3D00L, 0xEE2A0800L),
            Triple("Shield Blue", 0xFF2979FFL, 0xEE00103AL),
            Triple("Critical Hit", 0xFFFFEA00L, 0xEE2E2200L)
        )
        for (i in 0 until 5) {
            gamingColors.forEachIndexed { idx, (name, fg, bg) ->
                val id = "game_${i}_$idx"
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name Tier ${i + 1}",
                        category = "Gaming",
                        colorArgb = fg,
                        bgArgb = bg,
                        strokeColorArgb = 0xFF000000L,
                        shadowColorArgb = fg,
                        fontFamily = "sans",
                        isBold = true,
                        isItalic = i >= 2,
                        sizeScale = 1.15f
                    )
                )
            }
        }

        // 8. Luxury & Fashion (50 presets)
        val luxuryTints = listOf(
            Triple("Champagne Imperial", 0xFFF7E7CEL, 0xCC1A1610L),
            Triple("Rose Gold", 0xFFB76E79L, 0xCC180A0EL),
            Triple("Platinum Pure", 0xFFE5E4E2L, 0xCC0E0E10L),
            Triple("Royal Emerald", 0xFF50C878L, 0xCC05180EL),
            Triple("Sapphire Crown", 0xFF0F52BAL, 0xCC040D1CL),
            Triple("Velvet Noir", 0xFFF8F8F8L, 0xDD000000L),
            Triple("Vogue Editorial", 0xFFFFFFFFL, null),
            Triple("Haute Couture", 0xFFD4AF37L, null),
            Triple("Monaco Marble", 0xFFEDEAE6L, 0x99242322L),
            Triple("Silk Bronze", 0xFFCD7F32L, 0xCC160E08L)
        )
        for (i in 0 until 5) {
            luxuryTints.forEachIndexed { idx, (name, fg, bg) ->
                val id = "lux_${i}_$idx"
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name Collection ${i + 1}",
                        category = "Luxury",
                        colorArgb = fg,
                        bgArgb = bg,
                        strokeColorArgb = null,
                        shadowColorArgb = if (bg == null) 0x99000000L else null,
                        fontFamily = "serif",
                        isBold = i == 0,
                        isItalic = i == 1 || i == 3,
                        sizeScale = if (i == 4) 0.9f else 1.05f
                    )
                )
            }
        }

        // 9. Comic & Pop Art (50 presets)
        val comicThemes = listOf(
            Triple("POW Yellow", 0xFFFFEB3BL, 0xFFD50000L),
            Triple("BAM Cyan", 0xFF00E5FFL, 0xFF2962FFL),
            Triple("BOOM Orange", 0xFFFF6D00L, 0xFF212121L),
            Triple("Bubblegum Pink", 0xFFFF4081L, 0xFFFFFFFFL),
            Triple("Hero Lime", 0xFF76FF03L, 0xFF263238L),
            Triple("Action Violet", 0xFFAA00FFL, 0xFFFFEA00L),
            Triple("Sticker Pop", 0xFF000000L, 0xFFFFFFFFL),
            Triple("Cartoon Aqua", 0xFF18FFFFL, 0xFFFF1744L),
            Triple("Comic Ink", 0xFFFFFFFFL, 0xFF000000L),
            Triple("Splash Magenta", 0xFFFF0055L, 0xFFFFEB3BL)
        )
        for (i in 0 until 5) {
            comicThemes.forEachIndexed { idx, (name, fg, bg) ->
                val id = "comic_${i}_$idx"
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name Issue #${i + 1}",
                        category = "Comic Pop",
                        colorArgb = fg,
                        bgArgb = bg,
                        strokeColorArgb = 0xFF000000L,
                        shadowColorArgb = 0xFF000000L,
                        fontFamily = "sans",
                        isBold = true,
                        isItalic = i % 2 == 1,
                        sizeScale = 1.2f
                    )
                )
            }
        }

        // 10. Tech & Glitch (50 presets)
        val techThemes = listOf(
            Triple("Kernel Terminal", 0xFF00FF00L, 0xEE001100L),
            Triple("Holo Grid", 0xFF00E5FFL, 0xAA001B30L),
            Triple("Cyber Glitch Red", 0xFFFF1744L, 0xEE0A0A0AL),
            Triple("Quantum State", 0xFF7C4DFFL, 0xEE0E051AL),
            Triple("Telemetry Amber", 0xFFFFAB00L, 0xDD120B00L),
            Triple("Radar Sweep", 0xFF1DE9B6L, 0xEE001812L),
            Triple("Debug Alert", 0xFFFF5252L, 0xFF212121L),
            Triple("SciFi HUD Blue", 0xFF40C4FFL, 0xCC001020L),
            Triple("Sub-Zero Code", 0xFFE0F7FAL, 0xDD00202BL),
            Triple("AI Core Pulse", 0xFFE040FBL, 0xEE16001FL)
        )
        for (i in 0 until 5) {
            techThemes.forEachIndexed { idx, (name, fg, bg) ->
                val id = "tech_${i}_$idx"
                list.add(
                    TextPreset(
                        id = id,
                        name = "$name Mod ${i + 1}",
                        category = "Tech & Glitch",
                        colorArgb = fg,
                        bgArgb = bg,
                        strokeColorArgb = if (i == 2) 0xFF00E5FFL else null,
                        shadowColorArgb = fg,
                        fontFamily = "monospace",
                        isBold = true,
                        isItalic = false,
                        sizeScale = 0.95f
                    )
                )
            }
        }

        list
    }

    fun getPresetsForCategory(category: String): List<TextPreset> {
        return presets.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun findPresetById(id: String?): TextPreset? {
        if (id == null) return null
        return presets.firstOrNull { it.id == id }
    }
}
