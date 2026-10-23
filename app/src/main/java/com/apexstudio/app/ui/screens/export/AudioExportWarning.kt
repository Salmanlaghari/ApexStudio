package com.apexstudio.app.ui.screens.export

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette

/** Kept above export settings so a phone-sized viewport cannot hide missing audio. */
@Composable
internal fun AudioExportWarning(warning: String?) {
    if (warning == null) return
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Text(
            warning,
            modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = ApexPalette.Danger,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
