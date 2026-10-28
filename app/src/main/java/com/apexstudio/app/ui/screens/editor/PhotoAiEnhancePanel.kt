package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.presentation.viewmodel.PhotoAiEnhanceUiState
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
internal fun PhotoAiEnhanceTab(
    settings: PhotoEditSettings,
    adjustments: VideoAdjustments,
    aiState: PhotoAiEnhanceUiState,
    onAutoEnhance: () -> Unit,
    onDenoise: () -> Unit,
    onSharpen: () -> Unit,
    onUpscale: () -> Unit,
    onFaceCrop: () -> Unit,
    onStabilize: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose a tool to improve the selected photo.", color = Color(0xFF9CA3AF), fontSize = 12.sp)
        PhotoEnhanceAction("photo_ai_enhance_auto", "Auto enhance", "Balanced light, colour, clarity and sharpness", !adjustments.isDefault, onAutoEnhance)
        PhotoEnhanceAction(
            "photo_ai_enhance_upscale",
            "AI upscale",
            "Create a sharper 3× version; output is capped at 4.2 megapixels",
            settings.aiUpscaledImageUri != null,
            enabled = !aiState.processing,
            onClick = onUpscale
        )
        PhotoEnhanceAction("photo_ai_enhance_denoise", "Denoise", "Reduce grain and image noise", adjustments.denoise > 0f, onDenoise)
        PhotoEnhanceAction("photo_ai_enhance_sharpen", "Sharpen", "Bring out edge detail", adjustments.sharpness > 0f, onSharpen)
        PhotoEnhanceAction(
            "photo_ai_enhance_stabilize",
            "Stabilize",
            "Still-photo mode auto-levels small camera-roll angles with a safe crop; it does not remove motion blur",
            settings.stabilizePhoto,
            enabled = !aiState.processing,
            onClick = { onStabilize(!settings.stabilizePhoto) }
        )
        PhotoEnhanceAction(
            "photo_ai_enhance_face_crop",
            "Face crop",
            "Finds a face and suggests a tighter crop; no face leaves the photo unchanged",
            active = false,
            enabled = !aiState.processing,
            onClick = onFaceCrop
        )
        if (aiState.processing) {
            Text(
                if (aiState.progress01 > 0f) "${(aiState.progress01 * 100).toInt()}% · ${aiState.message}" else aiState.message,
                color = ApexPalette.NeonCyan,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
            Text(
                "Cancel",
                color = Color(0xFFFF8899),
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 4.dp, vertical = 2.dp)
            )
        } else if (aiState.message.isNotBlank()) {
            Text(aiState.message, color = Color(0xFF9CA3AF), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun PhotoEnhanceAction(
    tag: String,
    title: String,
    detail: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (active) ApexPalette.NeonCyan.copy(alpha = 0.12f) else Color(0xFF1E1E2E))
            .border(1.dp, if (active) ApexPalette.NeonCyan else Color(0xFF2A2A3C), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) ApexPalette.TextPrimary else Color(0xFF737B8C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFF9CA3AF), fontSize = 11.sp)
        }
        if (active) Text("On", color = ApexPalette.NeonCyan, fontSize = 11.sp)
    }
}
