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
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
internal fun PhotoAiEnhanceTab(
    adjustments: VideoAdjustments,
    onAutoEnhance: () -> Unit,
    onDenoise: () -> Unit,
    onSharpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Runs on this device using the photo renderer; no upload or model download.", color = Color(0xFF9CA3AF), fontSize = 12.sp)
        PhotoEnhanceAction("photo_ai_enhance_auto", "Auto enhance", "Balanced light, colour, clarity and sharpness", !adjustments.isDefault, onAutoEnhance)
        PhotoEnhanceUnavailable("photo_ai_enhance_upscale_unavailable", "Upscale", "Standalone still-photo inference is unavailable; AI Video Upscale supports eligible project videos only.")
        PhotoEnhanceAction("photo_ai_enhance_denoise", "Denoise", "Local spatial denoise stage", adjustments.denoise > 0f, onDenoise)
        PhotoEnhanceAction("photo_ai_enhance_sharpen", "Sharpen", "Local edge/detail stage", adjustments.sharpness > 0f, onSharpen)
        PhotoEnhanceUnavailable("photo_ai_enhance_stabilize_unavailable", "Stabilize", "Unavailable for still photos; stabilization requires video frames.")
        PhotoEnhanceUnavailable("photo_ai_enhance_face_crop_unavailable", "Face crop", "Unavailable for still photos; video face-crop tracks are rejected by project export.")
    }
}

@Composable
private fun PhotoEnhanceAction(tag: String, title: String, detail: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (active) ApexPalette.NeonCyan.copy(alpha = 0.12f) else Color(0xFF1E1E2E))
            .border(1.dp, if (active) ApexPalette.NeonCyan else Color(0xFF2A2A3C), RoundedCornerShape(10.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFF9CA3AF), fontSize = 11.sp)
        }
        if (active) Text("On", color = ApexPalette.NeonCyan, fontSize = 11.sp)
    }
}

@Composable
private fun PhotoEnhanceUnavailable(tag: String, title: String, reason: String) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF171722)).border(1.dp, Color(0xFF2A2A3C), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp).testTag(tag)
    ) {
        Text("$title · Unavailable", color = Color(0xFF9CA3AF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(reason, color = Color(0xFF737B8C), fontSize = 11.sp)
    }
}
