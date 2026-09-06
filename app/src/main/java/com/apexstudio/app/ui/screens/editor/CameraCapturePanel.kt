package com.apexstudio.app.ui.screens.editor

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.ui.theme.ApexPalette
import java.io.File

@Composable
fun CameraCapturePanel(
    onCapturePicked: (List<MediaMetadata>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVideoMode by remember { mutableStateOf(true) }
    var currentCaptureFile by remember { mutableStateOf<File?>(null) }
    var currentCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && currentCaptureFile != null && currentCaptureFile!!.exists() && currentCaptureFile!!.length() > 0) {
            val file = currentCaptureFile!!
            val meta = MediaMetadata(
                uri = file.absolutePath,
                name = file.name,
                durationMs = 10_000L,
                width = 1920,
                height = 1080,
                fps = 30,
                type = ClipType.VIDEO
            )
            onCapturePicked(listOf(meta))
            onClose()
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentCaptureFile != null && currentCaptureFile!!.exists() && currentCaptureFile!!.length() > 0) {
            val file = currentCaptureFile!!
            val meta = MediaMetadata(
                uri = file.absolutePath,
                name = file.name,
                durationMs = 5_000L,
                width = 1920,
                height = 1080,
                fps = 30,
                type = ClipType.OVERLAY
            )
            onCapturePicked(listOf(meta))
            onClose()
        }
    }

    fun launchCamera() {
        try {
            val dir = File(context.getExternalFilesDir(null), "CameraCaptures")
            dir.mkdirs()
            val ext = if (isVideoMode) ".mp4" else ".jpg"
            val file = File(dir, "Cap_${System.currentTimeMillis()}$ext")
            currentCaptureFile = file
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            currentCaptureUri = uri

            if (isVideoMode) {
                videoLauncher.launch(uri)
            } else {
                photoLauncher.launch(uri)
            }
        } catch (e: Exception) {
            Log.e("CameraCapturePanel", "Failed to launch camera", e)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Camera Studio",
                color = ApexPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Mode Switcher (Video vs Photo)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isVideoMode) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color.Transparent)
                    .border(if (isVideoMode) 1.dp else 0.dp, ApexPalette.NeonCyan, RoundedCornerShape(8.dp))
                    .clickable { isVideoMode = true }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = if (isVideoMode) ApexPalette.NeonCyan else ApexPalette.TextSecondary, modifier = Modifier.size(14.dp))
                    Text("Video", color = if (isVideoMode) ApexPalette.NeonCyan else ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isVideoMode) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color.Transparent)
                    .border(if (!isVideoMode) 1.dp else 0.dp, ApexPalette.NeonCyan, RoundedCornerShape(8.dp))
                    .clickable { isVideoMode = false }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = if (!isVideoMode) ApexPalette.NeonCyan else ApexPalette.TextSecondary, modifier = Modifier.size(14.dp))
                    Text("Photo", color = if (!isVideoMode) ApexPalette.NeonCyan else ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Capture Shutter Button
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                    )
                )
                .clickable { launchCamera() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgDeep)
                    .border(2.dp, ApexPalette.NeonCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isVideoMode) Icons.Default.Videocam else Icons.Default.CameraAlt,
                    contentDescription = "Capture",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            if (isVideoMode) "Tap shutter to record high quality video"
            else "Tap shutter to capture photo overlay",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp
        )
    }
}
