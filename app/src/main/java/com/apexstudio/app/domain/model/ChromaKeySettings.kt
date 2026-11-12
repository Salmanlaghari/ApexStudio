package com.apexstudio.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChromaKeySettings(
    val enabled: Boolean = false,
    val keyColorArgb: Long = 0xFF00E676, // Default Green Screen
    val similarity: Float = 0.40f,
    val smoothness: Float = 0.15f,
    val spillSuppression: Float = 0.50f,
    val depth3D: Float = 0.35f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    /** Stable wallpaper ID for catalog assets; legacy values are resolved by WallpaperCatalogRepository. */
    val backgroundType: String = "cyber-portal",
    val customBackgroundUri: String? = null
)

fun ChromaKeySettings.hasValidParameters(): Boolean =
    keyColorArgb in Int.MIN_VALUE.toLong()..0xFFFF_FFFFL &&
        similarity.isFinite() && similarity in 0.01f..1f &&
        smoothness.isFinite() && smoothness in 0.01f..0.5f &&
        spillSuppression.isFinite() && spillSuppression in 0f..1f &&
        depth3D.isFinite() && depth3D in 0f..1f &&
        tiltX.isFinite() && tiltX in -1f..1f &&
        tiltY.isFinite() && tiltY in -1f..1f

/** Clamp numeric values; normalize valid signed ARGB values and default colors outside 32-bit ARGB. */
fun ChromaKeySettings.sanitized(): ChromaKeySettings = copy(
    keyColorArgb = if (keyColorArgb in Int.MIN_VALUE.toLong()..0xFFFF_FFFFL) {
        keyColorArgb and 0xFFFF_FFFFL
    } else 0xFF00E676L,
    similarity = similarity.takeIf { it.isFinite() }?.coerceIn(0.01f, 1f) ?: 0.4f,
    smoothness = smoothness.takeIf { it.isFinite() }?.coerceIn(0.01f, 0.5f) ?: 0.15f,
    spillSuppression = spillSuppression.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
    depth3D = depth3D.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.35f,
    tiltX = tiltX.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
    tiltY = tiltY.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
)
