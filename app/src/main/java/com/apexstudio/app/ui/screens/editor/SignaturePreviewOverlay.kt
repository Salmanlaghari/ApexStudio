package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.apexstudio.app.domain.model.Signature
import com.apexstudio.app.domain.model.SignatureCorner

/** Project branding rendered in the editor frame above text, stickers, and PiP. */
@Composable
internal fun SignaturePreviewOverlay(
    signature: Signature?,
    modifier: Modifier = Modifier
) {
    val value = signature ?: return
    if (value.text.isBlank() && value.logoUri == null) return

    val alignment = when (value.position) {
        SignatureCorner.TOP_LEFT -> Alignment.TopStart
        SignatureCorner.TOP_RIGHT -> Alignment.TopEnd
        SignatureCorner.BOTTOM_LEFT -> Alignment.BottomStart
        SignatureCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
    val fontSize = value.fontSize.takeIf { it.isFinite() }?.coerceIn(12f, 256f) ?: 24f
    val margin = value.marginDp.takeIf { it.isFinite() }?.coerceIn(0f, 256f) ?: 16f
    val alpha = value.alpha.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(alignment)
                .padding(margin.dp)
                .graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            value.logoUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Signature logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size((fontSize * 1.25f).dp)
                )
            }
            if (value.text.isNotBlank()) {
                Text(
                    text = value.text,
                    color = value.color,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
