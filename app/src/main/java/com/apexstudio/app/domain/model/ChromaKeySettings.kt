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
    val backgroundType: String = "cyber_portal", // cyber_portal, neon_city, virtual_studio, deep_space, matrix_grid, transparent, custom
    val customBackgroundUri: String? = null
)
