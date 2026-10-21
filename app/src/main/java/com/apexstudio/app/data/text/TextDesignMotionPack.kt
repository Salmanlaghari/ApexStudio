package com.apexstudio.app.data.text

import com.apexstudio.app.domain.model.TextOverlay
import java.util.Locale

/** A named, editable text look paired with an animation understood by [TextAnimEngine]. */
data class TextDesignMotionPreset(
    val id: String,
    val name: String,
    val designName: String,
    val motionName: String,
    val category: String,
    val colorArgb: Long,
    val bgArgb: Long?,
    val strokeColorArgb: Long?,
    val shadowColorArgb: Long?,
    val fontFamily: String,
    val isBold: Boolean,
    val isItalic: Boolean,
    val sizeScale: Float,
    val textAlign: String,
    val letterSpacingEm: Float,
    val gradient: Pair<Long, Long>?,
    val animationType: String,
    val animationDurationMs: Long
) {
    /** Replaces only visual styling and motion; user text, timing, transform and masks survive. */
    fun applyTo(overlay: TextOverlay): TextOverlay = overlay.copy(
        presetId = id,
        colorArgb = colorArgb,
        bgArgb = bgArgb,
        strokeColorArgb = strokeColorArgb,
        shadowColorArgb = shadowColorArgb,
        fontFamily = fontFamily,
        isBold = isBold,
        isItalic = isItalic,
        sizeScale = sizeScale,
        textAlign = textAlign,
        letterSpacingEm = letterSpacingEm,
        gradientStartArgb = gradient?.first,
        gradientEndArgb = gradient?.second,
        animationType = animationType,
        animationDurationMs = animationDurationMs
    )
}

/**
 * 120 original named combinations: twelve materially different designs × ten
 * animation operations implemented by TextAnimEngine. They are combinations,
 * not claims of 120 unique animation algorithms. Every field is renderer-backed.
 */
object TextDesignMotionPack {
    data class Design(
        val name: String,
        val category: String,
        val color: Long,
        val background: Long? = null,
        val stroke: Long? = null,
        val shadow: Long? = null,
        val font: String = "sans",
        val bold: Boolean = true,
        val italic: Boolean = false,
        val size: Float = 1f,
        val align: String = "CENTER",
        val spacing: Float = 0f,
        val gradient: Pair<Long, Long>? = null
    )

    data class Motion(val id: String, val label: String, val durationMs: Long)

    // Intentionally only IDs whose behavior is implemented in TextAnimEngine.
    val motions = listOf(
        Motion("FADE_IN", "Fade In", 700L),
        Motion("SLIDE_UP", "Rise", 800L),
        Motion("TYPEWRITER", "Typewriter", 1100L),
        Motion("POP_SPRING", "Spring Pop", 750L),
        Motion("BLUR_IN", "Focus In", 850L),
        Motion("SLIDE_BOUNCE", "Bounce Rise", 900L),
        Motion("PULSE", "Pulse Loop", 800L),
        Motion("BOUNCE", "Hop Loop", 800L),
        Motion("3D_FLIP_X", "Flip X", 900L),
        Motion("3D_ROTATE_Y", "Spin Y", 900L)
    )

    val designs = listOf(
        Design("Amber Frame", "Editorial", 0xFFFFD166L, 0xCC18120AL, 0xFF3B2610L, 0xAA000000L, "serif", size = 1.12f, spacing = 0.035f, gradient = 0xFFFFE29AL to 0xFFE07A18L),
        Design("Ice Signal", "Neon", 0xFFB8F4FFL, null, 0xFF063849L, 0xFF00BDEFL, "monospace", size = 1.06f, spacing = 0.055f),
        Design("Rose Ledger", "Editorial", 0xFFFFE4E8L, 0xCC351522L, null, 0xAA12050AL, "serif", italic = true, size = 1.08f, align = "LEFT"),
        Design("Citrus Block", "Graphic", 0xFF14220CL, 0xFFE8F58AL, 0xFF263B0FL, null, "sans", size = 1.18f, spacing = 0.015f),
        Design("Violet Outline", "Neon", 0xFFE8D4FFL, null, 0xFF5411A0L, 0xFF9B35FFL, "sans", size = 1.1f, spacing = 0.045f, gradient = 0xFFE8D4FFL to 0xFFB45CFFL),
        Design("Harbor Tag", "Broadcast", 0xFFFFFFFFL, 0xFF075A78L, 0xFF042C45L, 0xAA001018L, "sans", size = 0.96f, align = "LEFT"),
        Design("Copper Mono", "Retro", 0xFFFFB777L, 0xDD24140BL, 0xFF5E2F16L, 0xCC000000L, "monospace", italic = true, size = 0.94f, spacing = 0.025f),
        Design("Paper Cut", "Minimal", 0xFF18212BL, 0xFFF4F0E8L, 0xFFB5ADA0L, null, "serif", bold = false, size = 1.04f),
        Design("Mint Capsule", "Social", 0xFF062B27L, 0xFF77E7C3L, null, 0x66000000L, "sans", size = 1.02f, align = "RIGHT"),
        Design("Scarlet Score", "Broadcast", 0xFFFFF7F1L, 0xFFB72F35L, 0xFF5B1119L, 0xCC000000L, "sans", size = 1.15f, spacing = 0.02f),
        Design("Blue Hour", "Cinematic", 0xFFE2EEFFL, 0xB8102540L, 0xFF192D4BL, 0xCC000000L, "serif", italic = true, size = 0.98f, gradient = 0xFFFFFFFFL to 0xFF92B9F5L),
        Design("Lime Circuit", "Gaming", 0xFFB9FF70L, null, 0xFF17310BL, 0xFF4D9B18L, "monospace", size = 1.12f, spacing = 0.07f)
    )

    val presets: List<TextDesignMotionPreset> by lazy {
        designs.flatMapIndexed { designIndex, design ->
            motions.map { motion ->
                val id = "tdm_${designIndex}_${motion.id.lowercase()}"
                TextDesignMotionPreset(
                    id = id,
                    name = "${design.name} · ${motion.label}",
                    designName = design.name,
                    motionName = motion.label,
                    category = design.category,
                    colorArgb = design.color,
                    bgArgb = design.background,
                    strokeColorArgb = design.stroke,
                    shadowColorArgb = design.shadow,
                    fontFamily = design.font,
                    isBold = design.bold,
                    isItalic = design.italic,
                    sizeScale = design.size,
                    textAlign = design.align,
                    letterSpacingEm = design.spacing,
                    gradient = design.gradient,
                    animationType = motion.id,
                    animationDurationMs = motion.durationMs
                )
            }
        }
    }

    fun search(query: String): List<TextDesignMotionPreset> {
        val tokens = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (tokens.isEmpty()) presets else presets.filter { preset ->
            val haystack = "${preset.name} ${preset.category} ${preset.designName} ${preset.motionName}".lowercase(Locale.ROOT)
            tokens.all { it in haystack }
        }
    }
}
