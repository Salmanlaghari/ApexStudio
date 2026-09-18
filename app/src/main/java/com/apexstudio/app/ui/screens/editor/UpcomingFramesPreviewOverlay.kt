package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.media.ThumbnailExtractor
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.delay

/**
 * Overlay shown when tapping the video preview area.
 * Displays a short preview of the upcoming few seconds/frames with timestamps,
 * allowing instant scrubbing, lookahead navigation, and 3-second preview playback.
 */
@Composable
fun UpcomingFramesPreviewOverlay(
    visible: Boolean,
    videoUri: String?,
    currentTimeMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onPlayPreviewSnippet: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Auto-dismiss after 4.5 seconds of idle display
    LaunchedEffect(visible, currentTimeMs) {
        if (visible) {
            delay(4500L)
            onDismiss()
        }
    }

    // 4 Upcoming Target Times (+0.75s, +1.5s, +2.25s, +3.0s)
    val totalDur = durationMs.coerceAtLeast(1000L)
    val offsets = listOf(750L, 1500L, 2250L, 3000L)
    val upcomingItems = remember(currentTimeMs, durationMs) {
        offsets.map { off ->
            val targetTime = (currentTimeMs + off).coerceIn(0L, totalDur)
            val label = String.format(java.util.Locale.US, "+%.1fs", off / 1000f)
            Triple(off, targetTime, label)
        }
    }

    var upcomingThumbnails by remember(currentTimeMs, videoUri) {
        mutableStateOf<Map<Long, Bitmap>>(emptyMap())
    }

    LaunchedEffect(currentTimeMs, videoUri) {
        if (!visible) return@LaunchedEffect
        val uri = videoUri
        if (!uri.isNullOrBlank()) {
            val map = mutableMapOf<Long, Bitmap>()
            for ((_, targetTime, _) in upcomingItems) {
                try {
                    val frame = VideoThumbnailExtractor.extractFrame(context, uri, targetTime)
                    if (frame != null) {
                        map[targetTime] = frame
                    }
                } catch (_: Exception) {}
            }
            if (map.isEmpty()) {
                val fallbacks = ThumbnailExtractor.generateFilmstripFrames(4, 120, 80, currentTimeMs)
                upcomingItems.forEachIndexed { idx, (_, targetTime, _) ->
                    if (idx < fallbacks.size) {
                        map[targetTime] = fallbacks[idx]
                    }
                }
            }
            upcomingThumbnails = map
        } else {
            val fallbacks = ThumbnailExtractor.generateFilmstripFrames(4, 120, 80, currentTimeMs)
            val map = mutableMapOf<Long, Bitmap>()
            upcomingItems.forEachIndexed { idx, (_, targetTime, _) ->
                if (idx < fallbacks.size) {
                    map[targetTime] = fallbacks[idx]
                }
            }
            upcomingThumbnails = map
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xF00D0D18),
                            Color(0xF8151525)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            ApexPalette.NeonCyan.copy(alpha = 0.7f),
                            ApexPalette.NeonPurple.copy(alpha = 0.7f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row: Title, badge, and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(ApexPalette.NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = ApexPalette.NeonCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = "UPCOMING FRAMES",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ApexPalette.NeonPurple.copy(alpha = 0.25f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+3s Lookahead",
                                color = Color(0xFFC084FC),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick 3-second playback preview button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ApexPalette.NeonCyan)
                                .clickable(onClick = onPlayPreviewSnippet)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Preview 3s",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Horizontal Row of 4 Upcoming Frames
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    upcomingItems.forEach { (_, targetTime, offsetLabel) ->
                        val thumb = upcomingThumbnails[targetTime]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F1F30))
                                .border(1.dp, Color(0xFF33334E), RoundedCornerShape(8.dp))
                                .clickable {
                                    onSeekTo(targetTime)
                                    onDismiss()
                                }
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(64.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF181829), Color(0xFF262640))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Offset badge (+0.8s) at top-left
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = offsetLabel,
                                    color = ApexPalette.NeonCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Timestamp at bottom-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = TimeFormat.msToShort(targetTime),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
