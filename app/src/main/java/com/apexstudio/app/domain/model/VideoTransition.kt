package com.apexstudio.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Transform
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
    EFFECTS("Glitch & FX")
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
        )
    )

    fun getById(id: String?): TransitionDefinition? {
        if (id == null) return null
        return transitions.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: when (id.lowercase()) {
                "cross" -> transitions.first { it.id == "cross_dissolve" }
                "wipe" -> transitions.first { it.id == "wipe_left" }
                "zoom" -> transitions.first { it.id == "zoom_blur" }
                "slide" -> transitions.first { it.id == "slide_left" }
                else -> null
            }
    }

    fun getByCategory(category: TransitionCategory): List<TransitionDefinition> {
        return if (category == TransitionCategory.ALL) transitions
        else transitions.filter { it.category == category }
    }

    fun formatDuration(durationMs: Long): String {
        return String.format(java.util.Locale.US, "%.1fs", durationMs / 1000f)
    }
}
