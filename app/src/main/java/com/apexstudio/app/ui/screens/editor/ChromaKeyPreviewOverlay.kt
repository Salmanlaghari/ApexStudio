package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.apexstudio.app.data.engine.ChromaKeyProcessor
import com.apexstudio.app.domain.model.ChromaKeySettings

@Composable
fun ChromaKeyPreviewOverlay(
    settings: ChromaKeySettings,
    matteViewOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!settings.enabled) return

    val bgBrush = remember(settings.backgroundType) {
        when (settings.backgroundType) {
            "cyber_portal" -> Brush.radialGradient(
                listOf(Color(0xFF00E5FF).copy(alpha = 0.45f), Color(0xFF1A0A2A), Color(0xFF050510))
            )
            "neon_city" -> Brush.verticalGradient(
                listOf(Color(0xFFFF007F).copy(alpha = 0.4f), Color(0xFF2E0854), Color(0xFF0D0221))
            )
            "virtual_studio" -> Brush.verticalGradient(
                listOf(Color(0xFF2C3E50), Color(0xFF000000))
            )
            "deep_space" -> Brush.radialGradient(
                listOf(Color(0xFF1F1C2C), Color(0xFF928DAB).copy(alpha = 0.2f), Color(0xFF000000))
            )
            else -> Brush.verticalGradient(
                listOf(Color.Transparent, Color.Transparent)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background plate
        Box(modifier = Modifier.fillMaxSize().background(bgBrush))

        // 3D Perspective Grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizon = h * (0.55f + settings.tiltY * 0.1f)
            val lineCount = 8
            for (i in 0..lineCount) {
                val bottomX = (w * i / lineCount) + (settings.tiltX * 50f)
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                    start = Offset(w / 2f, horizon),
                    end = Offset(bottomX, h),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}
