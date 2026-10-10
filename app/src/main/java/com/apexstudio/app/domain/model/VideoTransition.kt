package com.apexstudio.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Transition applied between two adjacent video clips on the timeline.
 */
@Serializable
data class ClipTransition(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fromClipId: String,
    val toClipId: String,
    val type: String = "cross_dissolve",
    val durationMs: Long = 500L
)

enum class TransitionCategory(val label: String) {
    ALL("All"),
    DISSOLVE("Dissolve & Fade"),
    WIPE("Wipe"),
    MOTION("Motion & Slide"),
    EFFECTS("Glitch & FX"),
    GL_PRO("GL Pro")
}

/**
 * Definition of a professional video transition available in the catalog.
 */
data class TransitionDefinition(
    val id: String,
    val name: String,
    val category: TransitionCategory,
    val description: String,
    val defaultDurationMs: Long = 500L,
    val badgeText: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val tag: String? = null
)

/**
 * Professional library of video transitions for editing and rendering.
 */
object TransitionLibrary {

    val transitions: List<TransitionDefinition> = listOf(
        TransitionDefinition(
            id = "cross_dissolve",
            name = "Cross Dissolve",
            category = TransitionCategory.DISSOLVE,
            description = "Timeless cinematic blend mixing outgoing and incoming frames smoothly.",
            badgeText = "DISSOLVE",
            icon = Icons.Default.SwapHoriz,
            gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)),
            tag = "Popular"
        ),
        TransitionDefinition(
            id = "fade_black",
            name = "Fade to Black",
            category = TransitionCategory.DISSOLVE,
            description = "Dips to complete black between shots. Ideal for scene changes and chapter breaks.",
            badgeText = "DIP BLACK",
            icon = Icons.Default.Lens,
            gradientColors = listOf(Color(0xFF1F2937), Color(0xFF000000)),
            tag = "Cinematic"
        ),
        TransitionDefinition(
            id = "fade_white",
            name = "Flash to White",
            category = TransitionCategory.DISSOLVE,
            description = "High-energy bloom flash cut. Popular for beat drops, music videos, and action cuts.",
            badgeText = "FLASH",
            icon = Icons.Default.FlashOn,
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFFFFFFF)),
            tag = "Energetic"
        ),
        TransitionDefinition(
            id = "wipe_left",
            name = "Wipe Left",
            category = TransitionCategory.WIPE,
            description = "Horizontal directional wipe from right to left with soft feathered edge.",
            badgeText = "WIPE L",
            icon = Icons.Default.KeyboardArrowLeft,
            gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
        ),
        TransitionDefinition(
            id = "wipe_right",
            name = "Wipe Right",
            category = TransitionCategory.WIPE,
            description = "Horizontal directional wipe sweeping from left to right.",
            badgeText = "WIPE R",
            icon = Icons.Default.KeyboardArrowRight,
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF06B6D4))
        ),
        TransitionDefinition(
            id = "wipe_up",
            name = "Wipe Up",
            category = TransitionCategory.WIPE,
            description = "Smooth vertical curtain wipe revealing the next scene from bottom to top.",
            badgeText = "WIPE UP",
            icon = Icons.Default.KeyboardArrowUp,
            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
        ),
        TransitionDefinition(
            id = "wipe_down",
            name = "Wipe Down",
            category = TransitionCategory.WIPE,
            description = "Smooth vertical curtain wipe revealing the next scene from top to bottom.",
            badgeText = "WIPE DN",
            icon = Icons.Default.KeyboardArrowDown,
            gradientColors = listOf(Color(0xFFEC4899), Color(0xFFF43F5E))
        ),
        TransitionDefinition(
            id = "clock_wipe",
            name = "Clock Wipe",
            category = TransitionCategory.WIPE,
            description = "Radial clock-hand sweep revealing the incoming shot in a 360-degree arc.",
            badgeText = "CLOCK",
            icon = Icons.Default.Timelapse,
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
            tag = "Creative"
        ),
        TransitionDefinition(
            id = "zoom_blur",
            name = "Zoom Push",
            category = TransitionCategory.MOTION,
            description = "High-velocity kinetic optical zoom with radial blur blending the transition.",
            badgeText = "ZOOM",
            icon = Icons.Default.BlurCircular,
            gradientColors = listOf(Color(0xFFEC4899), Color(0xFF6366F1)),
            tag = "Trending"
        ),
        TransitionDefinition(
            id = "slide_left",
            name = "Slide Left",
            category = TransitionCategory.MOTION,
            description = "Kinetic push slide carrying outgoing clip left as incoming clip enters.",
            badgeText = "SLIDE L",
            icon = Icons.Default.CompareArrows,
            gradientColors = listOf(Color(0xFF6366F1), Color(0xFF06B6D4))
        ),
        TransitionDefinition(
            id = "slide_right",
            name = "Slide Right",
            category = TransitionCategory.MOTION,
            description = "Kinetic push slide carrying outgoing clip right as incoming clip enters.",
            badgeText = "SLIDE R",
            icon = Icons.Default.Transform,
            gradientColors = listOf(Color(0xFF14B8A6), Color(0xFF3B82F6))
        ),
        TransitionDefinition(
            id = "slide_up",
            name = "Slide Up",
            category = TransitionCategory.MOTION,
            description = "Vertical push slide moving up with elastic momentum.",
            badgeText = "SLIDE UP",
            icon = Icons.Default.ChangeCircle,
            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))
        ),
        TransitionDefinition(
            id = "glitch",
            name = "Digital Glitch",
            category = TransitionCategory.EFFECTS,
            description = "Cyberpunk digital glitch with horizontal scanline displacement and RGB split.",
            badgeText = "GLITCH",
            icon = Icons.Default.Grain,
            gradientColors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)),
            tag = "Cyberpunk"
        ),
        TransitionDefinition(
            id = "light_leak",
            name = "Light Leak",
            category = TransitionCategory.EFFECTS,
            description = "Warm anamorphic lens flare burst bleeding through the cut.",
            badgeText = "LEAK",
            icon = Icons.Default.BrightnessAuto,
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFFB923C)),
            tag = "Vintage"
        ),
        // Pro Phase 1: new GPU shader transitions
        TransitionDefinition(
            id = "cross_blur",
            name = "Cross Blur",
            category = TransitionCategory.DISSOLVE,
            description = "Dreamy defocus blend — both shots melt through a soft blur.",
            badgeText = "BLUR",
            icon = Icons.Default.BlurOn,
            gradientColors = listOf(Color(0xFF7C9AB5), Color(0xFF3B5B7C)),
            tag = "New"
        ),
        TransitionDefinition(
            id = "doorway",
            name = "Doorway",
            category = TransitionCategory.MOTION,
            description = "3D doorway swing — the outgoing shot opens like a door onto the next scene.",
            badgeText = "DOOR",
            icon = Icons.Default.MeetingRoom,
            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)),
            tag = "New"
        ),
        TransitionDefinition(
            id = "pixelize",
            name = "Pixelize",
            category = TransitionCategory.EFFECTS,
            description = "Retro mosaic dissolve — the frame breaks into pixels and reforms.",
            badgeText = "PIXEL",
            icon = Icons.Default.GridOn,
            gradientColors = listOf(Color(0xFF22D3EE), Color(0xFF0E7490)),
            tag = "New"
        ),
        TransitionDefinition(
            id = "crosswarp",
            name = "Cross Warp",
            category = TransitionCategory.EFFECTS,
            description = "Warp-speed sweep — both shots bow and rush past each other.",
            badgeText = "WARP",
            icon = Icons.Default.Waves,
            gradientColors = listOf(Color(0xFFF472B6), Color(0xFF7C3AED)),
            tag = "New"
        )
    )

    // Phase 2: runtime-registered pack transitions (from assets/packs/transitions/).
    // Pack GLSL that the GL engine doesn't know falls back to a safe default
    // dissolve at render time — entries here are always safe to display.
    @Volatile
    private var packTransitions: List<TransitionDefinition> = emptyList()
    @Volatile
    private var glProTransitions: List<TransitionDefinition> = emptyList()

    /** Built-in + pack transitions. */
    fun allTransitions(): List<TransitionDefinition> = transitions + packTransitions + glProTransitions

    fun hasGlProTransitions(): Boolean = glProTransitions.isNotEmpty()

    fun hasPackTransitions(): Boolean = packTransitions.isNotEmpty()

    /**
     * Registers raw pack transitions (from PackLoader) as [TransitionDefinition].
     * Pack categories map to the closest built-in [TransitionCategory].
     * Pack GLSL unknown to the GL engine falls back to a safe dissolve at
     * render time — entries here are always safe to display. Idempotent.
     */
    @Synchronized
    fun registerPackTransitions(
        packTransitions: List<com.apexstudio.app.data.packs.PackLoader.PackTransition>
    ) {
        if (this.packTransitions.isNotEmpty()) return
        val builtinIds = transitions.map { it.id }.toSet()
        this.packTransitions = packTransitions.map { t ->
            val cat = when (t.category.lowercase()) {
                "basic" -> TransitionCategory.DISSOLVE
                "3d" -> TransitionCategory.MOTION
                "glitch" -> TransitionCategory.EFFECTS
                else -> TransitionCategory.EFFECTS
            }
            TransitionDefinition(
                id = "pack_${t.id}",
                name = t.name,
                category = cat,
                description = "Pack transition: ${t.name}",
                defaultDurationMs = (t.duration * 1000).toLong(),
                badgeText = t.name.take(6).uppercase(),
                icon = Icons.Default.AutoAwesome,
                gradientColors = listOf(Color(0xFF7C3AED), Color(0xFF00E5FF)),
                tag = "Pack"
            )
        }.filter { it.id !in builtinIds }
    }

    /**
     * Phase 4: registers the 100+ ported gl-transitions shaders
     * (MIT, https://github.com/gl-transitions/gl-transitions) as
     * [TransitionDefinition]s with ids `glpro_<shaderId>`. The export
     * engine renders them via GlTransitionAdapterEffect; the preview
     * animates a generic dip-through-black approximation. Idempotent.
     */
    @Synchronized
    fun registerGlProTransitions(
        glTransitions: List<com.apexstudio.app.data.gl.GlTransitionInfo>
    ) {
        if (this.glProTransitions.isNotEmpty()) return
        val builtinIds = (transitions.map { it.id } + packTransitions.map { it.id }).toSet()
        // Deterministic accent per shader from its name hash.
        val accents = listOf(
            0xFF00E5FFL to 0xFF7C4DFFL,
            0xFFFF2D55L to 0xFFFF6D00L,
            0xFF00C853L to 0xFF00E5FFL,
            0xFFFFC400L to 0xFFFF2D55L,
            0xFF8B5CF6L to 0xFF00B0FFL
        )
        this.glProTransitions = glTransitions.map { t ->
            val (a, b) = accents[(t.id.hashCode() and Int.MAX_VALUE) % accents.size]
            TransitionDefinition(
                id = com.apexstudio.app.data.gl.GlTransitionAdapterEffect.ID_PREFIX + t.id,
                name = t.name,
                category = TransitionCategory.GL_PRO,
                description = "GL shader transition by ${t.author}.",
                defaultDurationMs = 700L,
                badgeText = "GL",
                icon = Icons.Default.AutoAwesome,
                gradientColors = listOf(Color(a), Color(b)),
                tag = "GL Pro"
            )
        }.filter { it.id !in builtinIds }
    }

    fun getById(id: String?): TransitionDefinition? {
        if (id == null) return null
        return allTransitions().firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: when (id.lowercase()) {
                "cross" -> transitions.first { it.id == "cross_dissolve" }
                "wipe" -> transitions.first { it.id == "wipe_left" }
                "zoom" -> transitions.first { it.id == "zoom_blur" }
                "slide" -> transitions.first { it.id == "slide_left" }
                else -> null
            }
    }

    fun getByCategory(category: TransitionCategory): List<TransitionDefinition> {
        return if (category == TransitionCategory.ALL) allTransitions()
        else allTransitions().filter { it.category == category }
    }

    fun formatDuration(durationMs: Long): String {
        return String.format(java.util.Locale.US, "%.1fs", durationMs / 1000f)
    }
}
