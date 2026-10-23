package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.stickers.StickerImageCache
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.ui.theme.EditorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reference-style quick selection, populated only by real, active project stickers. */
@Composable
internal fun ProjectStickerQuickTray(
    stickers: List<StickerOverlay>,
    selectedStickerId: String?,
    onSelectSticker: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (stickers.isEmpty()) return
    val context = LocalContext.current
    val cache = remember(context) { StickerImageCache(context) }
    Box(modifier.width(58.dp)) {
        Column(
            Modifier.padding(top = 8.dp, end = 6.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(EditorPalette.BgDeep.copy(alpha = 0.93f))
                .border(1.dp, EditorPalette.TextSecondary.copy(alpha = 0.8f), RoundedCornerShape(9.dp))
                .heightIn(max = 156.dp)
                .verticalScroll(rememberScrollState())
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            stickers.forEach { sticker ->
                val bitmap by produceState<android.graphics.Bitmap?>(null, sticker.assetPath) {
                    value = withContext(Dispatchers.IO) { sticker.assetPath?.let { cache.get(it) } }
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                        .border(1.dp, if (sticker.id == selectedStickerId) EditorPalette.NeonCyan else EditorPalette.BorderGlass, RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { onSelectSticker(sticker.id) }
                        .semantics { contentDescription = "Select project sticker ${sticker.name}" },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize().padding(3.dp))
                    else Text(if (sticker.assetPath.isNullOrBlank()) sticker.symbolOrUri else sticker.name.take(3),
                        color = EditorPalette.TextPrimary, fontSize = if (sticker.assetPath.isNullOrBlank()) 24.sp else 10.sp)
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape)
                .background(EditorPalette.BgDeep)
                .border(1.dp, EditorPalette.TextPrimary, CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "Close project sticker tray" },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Close, null, tint = EditorPalette.TextPrimary, modifier = Modifier.size(16.dp)) }
    }
}
