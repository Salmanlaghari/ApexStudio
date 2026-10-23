package com.apexstudio.app.camerakit

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.util.Locale

private const val CAMERA_KIT_MODULE = "camerakit"
private const val CAMERA_KIT_ACTIVITY = "com.apexstudio.app.camerakit.CameraKitActivity"

/** Explicit Play delivery only: never downloads executable code from arbitrary URLs. */
@Composable
fun CameraKitDeliveryRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { SplitInstallManagerFactory.create(context) }
    var sessionId by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(DeliveryPhase.READY) }
    var detail by remember { mutableStateOf<String?>(null) }
    var downloaded by remember { mutableLongStateOf(0) }
    var total by remember { mutableLongStateOf(0) }
    var confirmation by remember { mutableStateOf<SplitInstallSessionState?>(null) }
    var cancelRequested by remember { mutableStateOf(false) }
    var hasLaunchedFeature by remember { mutableStateOf(false) }
    val featureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onBack() }
    val confirmationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        confirmation = null
        phase = if (result.resultCode == Activity.RESULT_OK) DeliveryPhase.PENDING else DeliveryPhase.CANCELED
    }
    val applyState: (SplitInstallSessionState) -> Unit = { state ->
        if (CAMERA_KIT_MODULE in state.moduleNames()) {
            sessionId = state.sessionId()
            downloaded = state.bytesDownloaded()
            total = state.totalBytesToDownload()
            phase = deliveryPhase(state.status())
            confirmation = state.takeIf { it.status() == SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION }
            detail = if (state.status() == SplitInstallSessionStatus.FAILED)
                "Google Play could not install Camera Kit (error ${state.errorCode()}). Retry when connected to Play." else null
        }
    }
    val latestApplyState by rememberUpdatedState(applyState)
    val listener = remember(manager) { SplitInstallStateUpdatedListener { latestApplyState(it) } }
    DisposableEffect(manager, listener) {
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }
    LaunchedEffect(manager) {
        if (CAMERA_KIT_MODULE in manager.installedModules) phase = DeliveryPhase.INSTALLED
        else manager.sessionStates.addOnSuccessListener { states ->
            states.lastOrNull { CAMERA_KIT_MODULE in it.moduleNames() }?.let(latestApplyState)
        }
    }
    LaunchedEffect(phase, hasLaunchedFeature) {
        if (phase == DeliveryPhase.INSTALLED && !hasLaunchedFeature) {
            hasLaunchedFeature = true
            try {
                featureLauncher.launch(Intent().setClassName(context.packageName, CAMERA_KIT_ACTIVITY))
            } catch (_: RuntimeException) {
                phase = DeliveryPhase.FAILED
                detail = "The installed Camera Kit activity could not open. Restart the app and retry from its Play-installed version."
            }
        }
    }
    fun cancelSession(id: Int) {
        manager.cancelInstall(id).addOnFailureListener {
            phase = DeliveryPhase.FAILED
            detail = "Google Play could not cancel this request. Check the current installation state before retrying."
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Camera Kit Lenses", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(when (phase) {
            DeliveryPhase.READY -> "Download Camera Kit when you choose to use AR lenses. The core editor works without it."
            DeliveryPhase.PENDING -> "Waiting for Google Play…"
            DeliveryPhase.CONFIRMATION_REQUIRED -> "Google Play needs your approval to continue this download."
            DeliveryPhase.DOWNLOADING -> "Downloading Camera Kit…"
            DeliveryPhase.INSTALLING -> "Installing Camera Kit…"
            DeliveryPhase.CANCELING -> "Canceling Camera Kit download…"
            DeliveryPhase.INSTALLED -> "Camera Kit is ready. Opening lenses…"
            DeliveryPhase.CANCELED -> "Camera Kit download canceled. You can retry."
            DeliveryPhase.FAILED -> detail ?: "Camera Kit could not be downloaded. Check your connection and retry."
        }, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text("Requires a Google Play-distributed app bundle. A standalone sideloaded APK does not contain Camera Kit or provide Play feature delivery.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        when (phase) {
            DeliveryPhase.PENDING, DeliveryPhase.DOWNLOADING, DeliveryPhase.INSTALLING, DeliveryPhase.CANCELING -> {
                if (total > 0 && phase == DeliveryPhase.DOWNLOADING) {
                    LinearProgressIndicator(progress = { (downloaded.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(String.format(Locale.ROOT, "%.1f / %.1f MiB", downloaded / 1_048_576.0, total / 1_048_576.0))
                } else CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                if (phase != DeliveryPhase.CANCELING) OutlinedButton(onClick = {
                    cancelRequested = true
                    phase = DeliveryPhase.CANCELING
                    if (sessionId > 0) cancelSession(sessionId)
                }) { Text("Cancel download") }
            }
            DeliveryPhase.CONFIRMATION_REQUIRED -> Button(onClick = {
                val state = confirmation
                try {
                    if (state == null || !manager.startConfirmationDialogForResult(state, confirmationLauncher)) {
                        phase = DeliveryPhase.FAILED
                        detail = "Google Play could not open its confirmation. Retry the request."
                    }
                } catch (_: RuntimeException) {
                    phase = DeliveryPhase.FAILED
                    detail = "Google Play confirmation could not open. Retry from the Play-installed app."
                }
            }) { Text("Continue with Google Play") }
            DeliveryPhase.READY, DeliveryPhase.CANCELED, DeliveryPhase.FAILED -> Button(onClick = {
                detail = null
                cancelRequested = false
                if (CAMERA_KIT_MODULE in manager.installedModules) {
                    hasLaunchedFeature = false
                    phase = DeliveryPhase.INSTALLED
                } else {
                    phase = DeliveryPhase.PENDING
                    sessionId = 0
                    downloaded = 0
                    total = 0
                    manager.startInstall(SplitInstallRequest.newBuilder().addModule(CAMERA_KIT_MODULE).build())
                        .addOnSuccessListener { id ->
                            sessionId = id
                            if (cancelRequested) cancelSession(id)
                            else if (CAMERA_KIT_MODULE in manager.installedModules) phase = DeliveryPhase.INSTALLED
                        }.addOnFailureListener { error ->
                            phase = if (cancelRequested) DeliveryPhase.CANCELED else DeliveryPhase.FAILED
                            detail = if (error is SplitInstallException && error.errorCode == SplitInstallErrorCode.NETWORK_ERROR)
                                "Network error. Check your connection and retry." else
                                "Google Play could not start delivery. Install this app from its Play testing/release track, then retry."
                        }
                }
            }) { Text(if (phase == DeliveryPhase.READY) "Download Camera Kit" else "Retry") }
            DeliveryPhase.INSTALLED -> Unit
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}

internal fun deliveryPhase(status: Int): DeliveryPhase = when (status) {
    SplitInstallSessionStatus.PENDING -> DeliveryPhase.PENDING
    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> DeliveryPhase.CONFIRMATION_REQUIRED
    SplitInstallSessionStatus.DOWNLOADING, SplitInstallSessionStatus.DOWNLOADED -> DeliveryPhase.DOWNLOADING
    SplitInstallSessionStatus.INSTALLING -> DeliveryPhase.INSTALLING
    SplitInstallSessionStatus.CANCELING -> DeliveryPhase.CANCELING
    SplitInstallSessionStatus.INSTALLED -> DeliveryPhase.INSTALLED
    SplitInstallSessionStatus.CANCELED -> DeliveryPhase.CANCELED
    SplitInstallSessionStatus.FAILED -> DeliveryPhase.FAILED
    else -> DeliveryPhase.PENDING
}

internal enum class DeliveryPhase { READY, PENDING, CONFIRMATION_REQUIRED, DOWNLOADING, INSTALLING, CANCELING, INSTALLED, CANCELED, FAILED }
