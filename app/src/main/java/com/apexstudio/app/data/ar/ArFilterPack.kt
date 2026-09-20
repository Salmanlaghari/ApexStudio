package com.apexstudio.app.data.ar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ArFilterCategory(
    val id: String,
    val title: String,
    val icon: ImageVector
) {
    ALL("all", "All Packs", Icons.Default.AutoAwesome),
    BEAUTY("beauty", "Beauty & Glow", Icons.Default.FaceRetouchingNatural),
    FACE_EYES("face_eyes", "Face & Eyes", Icons.Default.Visibility),
    ANIME_ART("anime_art", "Anime & Cartoon", Icons.Default.Palette),
    BOKEH_RETRO("bokeh_retro", "Bokeh & Retro", Icons.Default.CameraAlt),
    FESTIVAL("festival", "Festivals & Frames", Icons.Default.Celebration)
}

data class ArFilterPreset(
    val id: String,
    val title: String,
    val category: ArFilterCategory,
    val subtitle: String,
    val defaultIntensity: Float = 0.85f,
    val hasEditableText: Boolean = false,
    val defaultGreetingText: String = "",
    val accentHex: Long = 0xFF00E5FF
)

object ArFilterCatalog {

    val FILTERS: List<ArFilterPreset> = listOf(
        // Beauty Smoothing
        ArFilterPreset(
            id = "ar_beauty_smooth",
            title = "Porcelain Smooth",
            category = ArFilterCategory.BEAUTY,
            subtitle = "Soft radiant glow & bilateral skin smoothing",
            defaultIntensity = 0.85f,
            accentHex = 0xFFFF7597
        ),
        // Skin Retouch
        ArFilterPreset(
            id = "ar_skin_retouch",
            title = "Radiant Retouch",
            category = ArFilterCategory.BEAUTY,
            subtitle = "Peach warmth, even tone & micro-pore softening",
            defaultIntensity = 0.80f,
            accentHex = 0xFFFF9E7A
        ),
        // Face Slimming
        ArFilterPreset(
            id = "ar_face_slimming",
            title = "V-Line Slimming",
            category = ArFilterCategory.FACE_EYES,
            subtitle = "Contour jawline shading & chin highlight tracking",
            defaultIntensity = 0.75f,
            accentHex = 0xFFA855F7
        ),
        // Big Eyes
        ArFilterPreset(
            id = "ar_big_eyes",
            title = "Anime Sparkle Eyes",
            category = ArFilterCategory.FACE_EYES,
            subtitle = "Eye enlargement, iris radiance & sparkle stars",
            defaultIntensity = 0.90f,
            accentHex = 0xFF00E5FF
        ),
        // Cartoon / Anime Style
        ArFilterPreset(
            id = "ar_cartoon_anime",
            title = "Manga Anime",
            category = ArFilterCategory.ANIME_ART,
            subtitle = "Cel-shaded contours, blush circles & starbursts",
            defaultIntensity = 0.85f,
            accentHex = 0xFFEC4899
        ),
        // Cyber Glitch Visor
        ArFilterPreset(
            id = "ar_cyber_visor",
            title = "Cyber HUD Visor",
            category = ArFilterCategory.ANIME_ART,
            subtitle = "Neon visor HUD tracking eye coordinates & glitch grid",
            defaultIntensity = 0.90f,
            accentHex = 0xFF06B6D4
        ),
        // Background Blur / Bokeh
        ArFilterPreset(
            id = "ar_bokeh_blur",
            title = "Portrait Bokeh",
            category = ArFilterCategory.BOKEH_RETRO,
            subtitle = "Optical lens depth blur outside face anchor",
            defaultIntensity = 0.75f,
            accentHex = 0xFF3B82F6
        ),
        // Retro Camera Grain
        ArFilterPreset(
            id = "ar_retro_grain",
            title = "Vintage 35mm",
            category = ArFilterCategory.BOKEH_RETRO,
            subtitle = "Analog film grain, viewfinder marks & date stamp",
            defaultIntensity = 0.80f,
            accentHex = 0xFFF59E0B
        ),
        // Festival / Occasion: Ganesh Chaturthi
        ArFilterPreset(
            id = "ar_ganesh_chaturthi",
            title = "Ganesh Chaturthi",
            category = ArFilterCategory.FESTIVAL,
            subtitle = "Devotional temple arch, golden bells & editable greeting",
            defaultIntensity = 0.95f,
            hasEditableText = true,
            defaultGreetingText = "Happy Ganesh Chaturthi",
            accentHex = 0xFFF59E0B
        ),
        // Festival / Occasion: Diya & Floral Frame
        ArFilterPreset(
            id = "ar_diya_flower",
            title = "Diya & Floral Garland",
            category = ArFilterCategory.FESTIVAL,
            subtitle = "Flickering earthen lamps, marigold garland & festive glow",
            defaultIntensity = 0.95f,
            hasEditableText = true,
            defaultGreetingText = "Shubh Deepotsav",
            accentHex = 0xFFEAB308
        ),
        // Festival / Occasion: Sacred Lotus Temple
        ArFilterPreset(
            id = "ar_temple_lotus",
            title = "Temple & Lotus Aura",
            category = ArFilterCategory.FESTIVAL,
            subtitle = "Golden temple filigree, lotus crown & sacred blessings",
            defaultIntensity = 0.90f,
            hasEditableText = true,
            defaultGreetingText = "Aashirwad & Prosperity",
            accentHex = 0xFFF97316
        )
    )

    fun getFilterById(id: String?): ArFilterPreset? {
        if (id == null) return null
        return FILTERS.firstOrNull { it.id == id }
    }
}
