package com.apexstudio.app.data.template

import android.graphics.ColorMatrix
import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.data.filter.ColorMatrixGlEffect
import com.apexstudio.app.domain.model.TextOverlay

/** A built-in title recipe; callers can choose the recipe but cannot supply title text. */
data class ProjectVideoTextTemplate(
    val id: String,
    val label: String,
    private val literalText: String,
    private val sizeScale: Float,
    private val colorArgb: Long,
    private val fontFamily: String,
    private val y: Float,
    private val strokeColorArgb: Long? = null,
    private val bgArgb: Long? = null,
) {
    /** Creates a new, independently editable overlay each time the template is applied. */
    fun createTextOverlay(motion: ProjectVideoTextMotion? = null): TextOverlay = TextOverlay.of(
        text = literalText,
        x = 0.5f,
        y = y,
        sizeScale = sizeScale,
        colorArgb = colorArgb,
        bgArgb = bgArgb,
        strokeColorArgb = strokeColorArgb,
        fontFamily = fontFamily,
        isBold = true,
        presetId = id,
        animationType = motion?.animationType ?: "NONE",
        animationDurationMs = motion?.durationMs ?: 800L,
    )
}

/** Fixed, input-free text entrance choices applied to a built-in literal title. */
data class ProjectVideoTextMotion(
    val id: String,
    val label: String,
    val animationType: String,
    val durationMs: Long = 800L,
)

/** A fixed video look and fixed title applied together as one editor action. */
data class ProjectVideoTemplate(
    val id: String,
    val label: String,
    val effectId: String,
    val textTemplateId: String,
)

/** A fixed literal 4x5 Android color matrix for deterministic video rendering. */
data class ProjectVideoEffect(
    val id: String,
    val label: String,
    private val matrixValues: List<Float>,
) {
    init {
        require(matrixValues.size == 20) { "A color matrix must contain 20 values" }
        require(matrixValues.all { it.isFinite() }) { "Color matrix values must be finite" }
    }

    /** Returns an independent Android matrix for use by the shared Media3 preview/export effect. */
    fun colorMatrix(): ColorMatrix = ColorMatrix(matrixValues.toFloatArray())
}

/** Small, curated built-in recipes for the select-only video editing tools. */
object ProjectVideoPresets {
    val textMotions: List<ProjectVideoTextMotion> = listOf(
        ProjectVideoTextMotion("fade-in", "Fade In · YOUR STORY", "FADE_IN"),
        ProjectVideoTextMotion("slide-up", "Slide Up · YOUR STORY", "SLIDE_UP"),
        ProjectVideoTextMotion("typewriter", "Typewriter · YOUR STORY", "TYPEWRITER"),
    )

    val textTemplates: List<ProjectVideoTextTemplate> = listOf(
        ProjectVideoTextTemplate(
            id = "title-bold", label = "Bold Title · YOUR STORY", literalText = "YOUR STORY",
            sizeScale = 1.35f, colorArgb = 0xFFFFFFFFL, fontFamily = "BebasNeue", y = 0.32f,
            strokeColorArgb = 0xCC000000L,
        ),
        ProjectVideoTextTemplate(
            id = "title-elegant", label = "Elegant Title · A MOMENT", literalText = "A MOMENT",
            sizeScale = 1.1f, colorArgb = 0xFFFFE7B0L, fontFamily = "PlayfairDisplay", y = 0.34f,
        ),
        ProjectVideoTextTemplate(
            id = "title-caption", label = "Caption Bar · MADE TO REMEMBER", literalText = "MADE TO REMEMBER",
            sizeScale = 0.8f, colorArgb = 0xFFFFFFFFL, fontFamily = "Poppins", y = 0.78f,
            bgArgb = 0x99000000L,
        ),
    )

    val videoTemplates: List<ProjectVideoTemplate> = listOf(
        ProjectVideoTemplate("warm-story", "Warm Story · YOUR STORY", "warm-film", "title-bold"),
        ProjectVideoTemplate("cool-moment", "Cool Moment · A MOMENT", "cool-fade", "title-elegant"),
    )

    // Fixed, modest matrix coefficients: RGB channels are bounded/offset gently and
    // alpha is unchanged. These use ColorMatrixGlEffect rather than the approximate
    // Compose-only GPU filter preview, allowing callers to share the exact same shader.
    val effects: List<ProjectVideoEffect> = listOf(
        // Original, conservative RGB recipes. "HD" names describe the color look only:
        // they do not reconstruct detail, change resolution, or synthesize HDR metadata.
        ProjectVideoEffect(
            id = "natural-hd", label = "Natural HD · Balanced Color", matrixValues = listOf(
                1.04f, 0f, 0f, 0f, -5f,
                0f, 1.04f, 0f, 0f, -5f,
                0f, 0f, 1.04f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
        ProjectVideoEffect(
            id = "daylight-clean", label = "Daylight · Clean Whites", matrixValues = listOf(
                1.02f, 0f, 0f, 0f, -2f,
                0f, 1.02f, 0f, 0f, -2f,
                0f, 0f, 1.02f, 0f, -2f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
        ProjectVideoEffect(
            id = "vivid-balanced", label = "Vivid · Balanced RGB", matrixValues = listOf(
                1.05f, -0.025f, -0.025f, 0f, 0f,
                -0.025f, 1.05f, -0.025f, 0f, 0f,
                -0.025f, -0.025f, 1.05f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
        ProjectVideoEffect(
            id = "warm-film", label = "Warm Film", matrixValues = listOf(
                1.04f, 0.02f, 0f, 0f, 4f,
                0.01f, 1.02f, 0f, 0f, 2f,
                0f, 0.01f, 0.96f, 0f, -2f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
        ProjectVideoEffect(
            id = "cool-fade", label = "Cool Fade", matrixValues = listOf(
                0.97f, 0f, 0.01f, 0f, -1f,
                0f, 0.99f, 0.01f, 0f, 0f,
                0.01f, 0f, 1.04f, 0f, 3f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
        ProjectVideoEffect(
            id = "mono", label = "Monochrome", matrixValues = listOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    /** A local, deterministic color adjustment; it is not a learned enhancement model. */
    val videoEnhancements: List<ProjectVideoEffect> = listOf(
        ProjectVideoEffect(
            id = "video-enhance-color-lift", label = "Video Enhance · Color Lift", matrixValues = listOf(
                1.121848f, -0.07436f, -0.007488f, 0f, -1.12f,
                -0.022152f, 1.06964f, -0.007488f, 0f, -1.12f,
                -0.022152f, -0.07436f, 1.136512f, 0f, -1.12f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    fun textTemplate(id: String): ProjectVideoTextTemplate? = textTemplates.firstOrNull { it.id == id }
    fun textMotion(id: String): ProjectVideoTextMotion? = textMotions.firstOrNull { it.id == id }
    fun videoTemplate(id: String): ProjectVideoTemplate? = videoTemplates.firstOrNull { it.id == id }
    fun effect(id: String): ProjectVideoEffect? =
        (effects + videoEnhancements).firstOrNull { it.id == id }

    /** Resolver shared by live preview and export; null/no known id leaves the video untouched. */
    @OptIn(UnstableApi::class)
    fun colorEffect(id: String?): ColorMatrixGlEffect? = id?.let(::effect)?.let { effect ->
        ColorMatrixGlEffect(effect.colorMatrix().array.copyOf())
    }
}
