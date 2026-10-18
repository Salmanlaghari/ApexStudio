package com.apexstudio.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.apexstudio.app.ai.mediapipe.BundledMediaPipeModels
import com.apexstudio.app.ai.mediapipe.GestureCommand
import com.apexstudio.app.ai.mediapipe.GestureCommandSession
import com.apexstudio.app.ai.mediapipe.GestureSessionAdapter
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Optional, reusable camera sheet for local hand gestures. This component never arms on its own:
 * [onArmRequested] is called only after an explicit tap and must synchronously invoke the parent's
 * gated `beginGestureEditing()`; a null result prevents even the camera permission prompt. [session]
 * must reflect that newly armed context before camera analysis starts. It emits bounded requests only when
 * [commandsAllowed] is true; keep that value closed by default and derive it from the same
 * fail-closed authorization state used by the owning editor. No protected AI action is invoked here.
 *
 * The parent owns context identity and session invalidation. On lifecycle stop, permission loss, or
 * camera/session cancellation this component disarms the parent session and closes each frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureCameraSheet(
    session: GestureCommandSession?,
    commandsAllowed: Boolean,
    disabledReason: String?,
    onArmRequested: () -> GestureCommandSession?,
    onDisarmRequested: () -> Unit,
    onDismissRequest: () -> Unit,
    onCommand: (sessionId: String, command: GestureCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val globalGateApproved = isGestureEditingApproved()
    var hasCameraPermission by remember { mutableStateOf(false) }
    var lifecycleResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val latestSession = rememberUpdatedState(session)
    val latestAllowed = rememberUpdatedState(commandsAllowed)
    val latestArm = rememberUpdatedState(onArmRequested)
    val latestDisarm = rememberUpdatedState(onDisarmRequested)
    val latestCommand = rememberUpdatedState(onCommand)

    LaunchedEffect(globalGateApproved) {
        hasCameraPermission = globalGateApproved && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        cameraError = if (granted) null else "Camera permission was not granted."
        val sessionStillArmed = latestSession.value?.armed == true
        val canContinue = granted && isGestureEditingApproved() &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            latestAllowed.value && sessionStillArmed
        if (!canContinue && sessionStillArmed) {
            latestDisarm.value()
        }
        if (granted && !isGestureEditingApproved()) {
            hasCameraPermission = false
            cameraError = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.GESTURE_EDITING)
        } else if (granted && !sessionStillArmed) {
            cameraError = "Camera permission granted. Tap Arm gesture control to start a new session."
        } else if (!granted) {
            cameraError = "Camera permission was not granted. Gesture session disarmed."
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    lifecycleResumed = true
                    hasCameraPermission = isGestureEditingApproved() && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                }
                Lifecycle.Event.ON_PAUSE -> {
                    lifecycleResumed = false
                    latestSession.value?.takeIf { it.armed }?.let { latestDisarm.value() }
                }
                Lifecycle.Event.ON_STOP -> {
                    lifecycleResumed = false
                    latestSession.value?.takeIf { it.armed }?.let { latestDisarm.value() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestSession.value?.takeIf { it.armed }?.let { latestDisarm.value() }
        }
    }

    // Effect cancellation is the single camera-session teardown path. The analyzer is serialized
    // on its own executor; detector close waits for any synchronous native inference to return.
    LaunchedEffect(
        previewView,
        lifecycleOwner,
        lifecycleResumed,
        hasCameraPermission,
        globalGateApproved,
        session?.id,
        session?.armed,
        commandsAllowed,
    ) {
        val moderationApproved = isGestureEditingApproved()
        val armedSession = session?.takeIf { it.armed }
        val view = previewView
        // The global fail-closed policy runs before CameraX, model materialization, or camera binding.
        if (!moderationApproved || !globalGateApproved || armedSession == null || !commandsAllowed ||
            !hasCameraPermission || !lifecycleResumed || view == null
        ) {
            if (armedSession != null &&
                (!globalGateApproved || !commandsAllowed || !hasCameraPermission || !lifecycleResumed)
            ) {
                latestDisarm.value()
            }
            return@LaunchedEffect
        }

        val active = AtomicBoolean(true)
        val detectorDuringLoad = AtomicReference<com.apexstudio.app.ai.mediapipe.MediaPipeLandmarkers.HandDetector?>(null)
        val analyzerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "gesture-mediapipe-analyzer").apply { isDaemon = true }
        }
        var provider: ProcessCameraProvider? = null
        var detector: com.apexstudio.app.ai.mediapipe.MediaPipeLandmarkers.HandDetector? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var startFailed = false
        val adapter = GestureSessionAdapter().apply { arm(armedSession.id) }
        val mainExecutor = ContextCompat.getMainExecutor(context)

        try {
            val cameraProvider = awaitCameraProvider(context, mainExecutor)
            provider = cameraProvider
            try {
                detector = withContext(Dispatchers.IO) {
                    BundledMediaPipeModels.openHands(context, maxHands = 2).also(detectorDuringLoad::set)
                }
                detectorDuringLoad.set(null)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { detectorDuringLoad.getAndSet(null)?.close() }
                throw cancelled
            }
            if (!active.get()) return@LaunchedEffect

            val previewUseCase = Preview.Builder().build().also { useCase ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                useCase.setSurfaceProvider(view.surfaceProvider)
            }
            preview = previewUseCase
            val analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetRotation(view.display?.rotation ?: android.view.Surface.ROTATION_0)
                .build()
            analysis = analysisUseCase
            val handDetector = checkNotNull(detector)
            analysisUseCase.setAnalyzer(analyzerExecutor) { frame ->
                var bitmap: Bitmap? = null
                try {
                    if (!active.get()) return@setAnalyzer
                    bitmap = frame.toGestureBitmap()
                    val timestampMs = frame.imageInfo.timestamp.takeIf { it > 0L }?.div(1_000_000L)
                        ?: SystemClock.elapsedRealtime()
                    val hands = if (bitmap == null) emptyList() else handDetector.detect(bitmap)
                    val command = adapter.update(
                        timestampMs = timestampMs,
                        hands = hands,
                        mirrorHorizontal = true, // Front-facing PreviewView mirrors its displayed x-axis.
                    )
                    if (command != null && active.get() && isGestureEditingApproved()) {
                        mainExecutor.execute {
                            if (active.get() && isGestureEditingApproved() && latestAllowed.value &&
                                latestSession.value?.let { it.armed && it.id == armedSession.id } == true
                            ) {
                                latestCommand.value(armedSession.id, command)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    adapter.disarm()
                    mainExecutor.execute {
                        if (active.get()) {
                            cameraError = failure.message ?: "Gesture camera processing failed."
                            latestDisarm.value()
                        }
                    }
                } finally {
                    bitmap?.recycle()
                    frame.close()
                }
            }
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                previewUseCase,
                analysisUseCase,
            )
            cameraError = null
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            startFailed = true
            cameraError = failure.message ?: "Could not start the local gesture camera."
        } finally {
            active.set(false)
            adapter.disarm()
            analysis?.clearAnalyzer()
            if (provider != null) {
                try {
                    val boundPreview = preview
                    val boundAnalysis = analysis
                    if (boundPreview != null && boundAnalysis != null) provider.unbind(boundPreview, boundAnalysis)
                } catch (_: Exception) {
                    // Provider may already have been torn down with its lifecycle owner.
                }
            }
            analyzerExecutor.shutdown()
            withContext(NonCancellable + Dispatchers.IO) { detector?.close() }
            val stillSameArmedSession = latestSession.value?.let {
                it.armed && it.id == armedSession.id
            } == true
            if (stillSameArmedSession &&
                (startFailed || !lifecycleResumed || !hasCameraPermission || !commandsAllowed ||
                    !globalGateApproved || !active.get())
            ) latestDisarm.value()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            if (session?.armed == true) onDisarmRequested()
            onDismissRequest()
        },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Gesture control", style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    !globalGateApproved -> GlobalModerationEnforcement.statusMessage(ProtectedAiAction.GESTURE_EDITING)
                    !commandsAllowed -> disabledReason?.takeIf { it.isNotBlank() }
                        ?: "Gesture commands are disabled until the editor's safety authorization is available."
                    !hasCameraPermission -> "Camera access is requested only after you choose to arm gesture control."
                    session?.armed == true -> "Armed for this editing session. Keep one hand in view; swipe to request trim or pinch to request zoom."
                    else -> "Gesture control is off. Tap Arm to start the local camera session."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                factory = { viewContext ->
                    PreviewView(viewContext).also { preview ->
                        preview.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = preview
                    }
                },
                update = { previewView = it },
            )
            cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (session?.armed == true) {
                    Button(onClick = onDisarmRequested) { Text("Disarm") }
                } else {
                    Button(
                        enabled = globalGateApproved && commandsAllowed && lifecycleResumed,
                        onClick = {
                            cameraError = null
                            if (!isGestureEditingApproved()) {
                                cameraError = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.GESTURE_EDITING)
                                return@Button
                            }
                            // Invoke the editor's own fail-closed session begin before asking Android
                            // for permission; rejected/no-context sessions never open the camera prompt.
                            if (latestArm.value()?.takeIf { it.armed && it.id.isNotBlank() } == null) return@Button
                            hasCameraPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasCameraPermission) {
                                // Android's permission dialog pauses this lifecycle. Do not silently
                                // resurrect its old capability when permission is later granted.
                                latestDisarm.value()
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                    ) { Text(if (hasCameraPermission) "Arm gesture control" else "Allow camera") }
                }
                TextButton(onClick = {
                    if (session?.armed == true) onDisarmRequested()
                    onDismissRequest()
                }) { Text("Close") }
            }
        }
    }
}

private suspend fun awaitCameraProvider(context: android.content.Context, mainExecutor: java.util.concurrent.Executor): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context.applicationContext)
        future.addListener(
            {
                if (continuation.isActive) {
                    try {
                        continuation.resume(future.get())
                    } catch (failure: Exception) {
                        continuation.resumeWith(Result.failure(failure))
                    }
                }
            },
            mainExecutor,
        )
    }

/**
 * Copy CameraX's RGBA plane into a caller-owned, upright, at-most-640px bitmap. The caller must
 * recycle the returned bitmap; the ImageProxy itself remains caller-owned and must be closed.
 */
private fun ImageProxy.toGestureBitmap(): Bitmap? {
    if (format != PixelFormat.RGBA_8888 || planes.size != 1 || width <= 0 || height <= 0) return null
    val plane = planes[0]
    if (plane.pixelStride < 4) return null
    val buffer = plane.buffer.duplicate()
    val base = buffer.position()
    val sampleStep = maxOf((width + MAX_ANALYSIS_SIDE - 1) / MAX_ANALYSIS_SIDE,
        (height + MAX_ANALYSIS_SIDE - 1) / MAX_ANALYSIS_SIDE)
    val sampledWidth = (width + sampleStep - 1) / sampleStep
    val sampledHeight = (height + sampleStep - 1) / sampleStep
    val pixels = IntArray(sampledWidth * sampledHeight)
    for (y in 0 until sampledHeight) {
        for (x in 0 until sampledWidth) {
            val sourceX = minOf(x * sampleStep, width - 1)
            val sourceY = minOf(y * sampleStep, height - 1)
            val offset = base + sourceY * plane.rowStride + sourceX * plane.pixelStride
            if (offset + 3 >= buffer.limit()) return null
            val red = buffer.get(offset).toInt() and 0xff
            val green = buffer.get(offset + 1).toInt() and 0xff
            val blue = buffer.get(offset + 2).toInt() and 0xff
            val alpha = buffer.get(offset + 3).toInt() and 0xff
            pixels[y * sampledWidth + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    val raw = Bitmap.createBitmap(sampledWidth, sampledHeight, Bitmap.Config.ARGB_8888)
    try {
        raw.setPixels(pixels, 0, sampledWidth, 0, 0, sampledWidth, sampledHeight)
        val rotation = imageInfo.rotationDegrees
        val upright = if (rotation == 0) raw else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { rotated ->
                if (rotated !== raw) raw.recycle()
            }
        }
        return upright
    } catch (failure: Throwable) {
        if (!raw.isRecycled) raw.recycle()
        throw failure
    }
}

private const val MAX_ANALYSIS_SIDE = 640

private fun isGestureEditingApproved(): Boolean = try {
    GlobalModerationEnforcement.requireApproved(ProtectedAiAction.GESTURE_EDITING)
    true
} catch (_: Exception) {
    false
}
