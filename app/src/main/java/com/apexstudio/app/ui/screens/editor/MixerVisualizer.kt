package com.apexstudio.app.ui.screens.editor

import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.vendor.visualizer.visualizer.BarVisualizer
import com.apexstudio.app.vendor.visualizer.visualizer.CircleLineVisualizer
import com.apexstudio.app.vendor.visualizer.base.BaseVisualizer

/**
 * Phase 4: live audio visualizer (vendored gauravk95/audio-visualizer-android,
 * Apache-2.0) for the Audio Mixer.
 *
 * Driven by the editor player's real audio session FFT — bars/circle dance
 * to the actual mix, which makes beat-sync editing visual. When the player
 * has no audio session yet (nothing played), shows a hint instead of a
 * dead view.
 */
@Composable
fun MixerVisualizer(
    audioSessionId: Int,
    modifier: Modifier = Modifier
) {
    var style by remember { mutableIntStateOf(0) } // 0 = bars, 1 = circle
    val context = LocalContext.current

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Live Visualizer",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Bars", "Circle").forEachIndexed { i, label ->
                    val sel = style == i
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (sel) ApexPalette.NeonCyan.copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { style = i }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B0E14))
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (audioSessionId != 0) {
                // Key on style so switching recreates the view cleanly.
                androidx.compose.runtime.key(style) {
                    val view = remember {
                        (if (style == 0) BarVisualizer(context)
                        else CircleLineVisualizer(context)).apply {
                            setColor(ApexPalette.NeonCyan.toArgb())
                            setDensity(0.8f)
                        }
                    }
                    DisposableEffect(audioSessionId) {
                        try {
                            view.setAudioSessionId(audioSessionId)
                        } catch (e: Exception) {
                            Log.w("MixerVisualizer", "audio session attach failed", e)
                        }
                        onDispose {
                            try {
                                (view as? BaseVisualizer)?.release()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    AndroidView(
                        factory = { view },
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                }
            } else {
                Text(
                    "▶ Play the timeline to see the live spectrum",
                    color = ApexPalette.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
