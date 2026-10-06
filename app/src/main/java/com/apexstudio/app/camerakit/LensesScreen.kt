package com.apexstudio.app.camerakit

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewStub
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Snap Camera Kit lenses screen: live camera preview with an AR lens carousel.
 *
 * Flow: camera permission -> CameraX preview source -> Camera Kit Session (API token from
 * BuildConfig) -> observe lens repository for the demo lens group -> tap a lens to apply.
 * The session is closed via [DisposableEffect] when the screen leaves composition.
 */
@Composable
fun LensesScreen(
    onBack: () -> Unit,
    viewModel: LensesViewModel = viewModel()
) {
    val context = LocalContext.current

    if (!CameraKitConfig.isConfigured) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AppTopBar(title = "Snap Lenses", onBack = onBack)
            TokenMissingMessage()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val deviceSupported = remember { com.snap.camerakit.supported(context) }
        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> hasPermission = granted }

        AppTopBar(title = "Snap Lenses", onBack = onBack)

        when {
            !deviceSupported -> UnsupportedDeviceMessage()
            !hasPermission -> CameraPermissionPrompt(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
            else -> CameraKitPreview(viewModel = viewModel)
        }
    }
}

@Composable
private fun CameraKitPreview(viewModel: LensesViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { CameraKitSessionManager(context) }

    val lenses by viewModel.lenses.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbind()
            manager.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                ViewStub(ctx).also { stub ->
                    manager.start(stub, lifecycleOwner, frontCamera = true)
                    manager.session?.let(viewModel::bind)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Flip-camera button (top-right over the preview).
        IconButton(
            onClick = { manager.flipCamera() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Flip camera",
                tint = Color.White
            )
        }

        // Lens carousel docked at the bottom.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 10.dp)
        ) {
            if (error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error.orEmpty(),
                        color = ApexPalette.Danger,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.dismissError() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (lenses.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = ApexPalette.NeonCyan
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Loading lenses…",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "No lens" cell clears the applied lens.
                    item(key = "none") {
                        LensCell(
                            name = "None",
                            iconUri = null,
                            selected = selectedId == null,
                            onClick = { viewModel.clearLens() }
                        )
                    }
                    items(lenses, key = { it.id }) { lens ->
                        LensCell(
                            name = lens.name,
                            iconUri = lens.iconUri,
                            selected = selectedId == lens.id,
                            onClick = { viewModel.applyLens(lens.id) }
                        )
                    }
                }
            }
            if (applying) {
                Text(
                    text = "Applying lens…",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LensCell(
    name: String,
    iconUri: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .then(
                    if (selected) Modifier.border(2.dp, ApexPalette.NeonCyan, CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick)
        ) {
            if (iconUri != null) {
                // Subtle placeholder so a slow/failed thumbnail load never renders
                // as an empty hole in the lens carousel.
                val thumbPlaceholder = remember {
                    ColorPainter(Color.White.copy(alpha = 0.12f))
                }
                AsyncImage(
                    model = iconUri,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    placeholder = thumbPlaceholder,
                    error = thumbPlaceholder,
                    fallback = thumbPlaceholder,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = name,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            color = if (selected) ApexPalette.NeonCyan else Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TokenMissingMessage() {
    CenteredMessage(
        icon = { Icon(Icons.Default.Face, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(48.dp)) },
        title = "Snap Camera Kit not configured",
        body = "Add your staging API token as SNAP_CAMERA_KIT_TOKEN " +
            "(env var, local.properties, or the GitHub Actions secret) and rebuild."
    )
}

@Composable
private fun UnsupportedDeviceMessage() {
    CenteredMessage(
        icon = { Icon(Icons.Default.VideocamOff, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(48.dp)) },
        title = "Device not supported",
        body = "This device does not meet Camera Kit's requirements (OpenGL ES 3.0+)."
    )
}

@Composable
private fun CameraPermissionPrompt(onRequest: () -> Unit) {
    CenteredMessage(
        icon = { Icon(Icons.Default.VideocamOff, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(48.dp)) },
        title = "Camera access needed",
        body = "ApexStudio needs camera permission to show the AR lens preview.",
        action = {
            Button(onClick = onRequest) { Text("Grant camera permission") }
        }
    )
}

@Composable
private fun CenteredMessage(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
