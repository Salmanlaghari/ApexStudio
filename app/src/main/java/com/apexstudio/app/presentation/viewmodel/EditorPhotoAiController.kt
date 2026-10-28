package com.apexstudio.app.presentation.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.ai.mediapipe.BundledMediaPipeModels
import com.apexstudio.app.ai.onnx.EspcnSuperResolutionAdapter
import com.apexstudio.app.ai.safety.ImageAiScope
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.data.photoedit.PhotoEditRenderer
import com.apexstudio.app.data.photoedit.PhotoFaceCropEngine
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.PhotoEditSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Progress/status for bounded, offline photo AI operations in Photo AI Enhance. */
data class PhotoAiEnhanceUiState(
    val processing: Boolean = false,
    val progress01: Float = 0f,
    val message: String = "",
    val outputUri: String? = null,
)

/** A result may update its original photo after UI selection changes, but never after project/source replacement. */
internal fun photoAiResultCanCommit(
    expectedProjectId: String,
    expectedClipId: String,
    expectedSourceUri: String,
    currentProjectId: String?,
    currentClip: MediaClip?,
    expectedPhotoEditSettings: PhotoEditSettings? = null,
): Boolean = currentProjectId == expectedProjectId && currentClip?.id == expectedClipId &&
    currentClip.type == ClipType.IMAGE && currentClip.uri == expectedSourceUri &&
    (expectedPhotoEditSettings == null || currentClip.photoEdit == expectedPhotoEditSettings)

/** Enhance an imported photo and persist its result for preview/export. */
fun EditorViewModel.applyPhotoAiUpscale(clipId: String) {
    val context = context?.applicationContext ?: run {
        _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(message = "The editor is not ready. Try again.")
        return
    }
    synchronized(this) {
        if (photoAiEnhanceJob?.isActive == true) return
        val project = _state.value.project
        val clip = project?.clips?.firstOrNull { it.id == clipId }
        if (project == null || clip?.type != ClipType.IMAGE || clip.uri.isBlank()) {
            _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(message = "Select a photo first.")
            return
        }
        val expectedProjectId = project.id
        val expectedSourceUri = clip.uri
        lateinit var request: Job
        request = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var raw: Bitmap? = null
            var inferenceBitmap: Bitmap? = null
            var enhanced: Bitmap? = null
            var resultFile: File? = null
            var committed = false
            publishPhotoAi(request) {
                PhotoAiEnhanceUiState(processing = true, message = "Preparing your photo…")
            }
            try {
                val dimensions = PhotoEditRenderer.readImageDimensions(context, expectedSourceUri)
                    ?: throw IllegalStateException("The selected image dimensions could not be read.")
                val (targetWidth, targetHeight) = EspcnSuperResolutionAdapter.boundedInputDimensions(
                    dimensions.first, dimensions.second
                )
                raw = PhotoEditRenderer.loadBitmap(context, expectedSourceUri, maxOf(targetWidth, targetHeight))
                    ?: throw IllegalStateException("The selected image could not be decoded.")
                inferenceBitmap = if (raw!!.width == targetWidth && raw!!.height == targetHeight) raw else
                    Bitmap.createScaledBitmap(raw!!, targetWidth, targetHeight, true)

                val adapter = EspcnSuperResolutionAdapter.fromBundledAssets(context)
                enhanced = ImageAiScope.withExistingProjectImage(clip, ProtectedAiAction.ONNX_UPSCALING) {
                    adapter.upscale(inferenceBitmap!!) { progress ->
                        publishPhotoAi(request) {
                            it.copy(
                                progress01 = progress.fraction.coerceIn(0f, 1f),
                                message = "Enhancing photo…"
                            )
                        }
                    }
                }
                if (inferenceBitmap !== raw && inferenceBitmap?.isRecycled == false) inferenceBitmap?.recycle()
                inferenceBitmap = null

                val outputBitmap = enhanced ?: throw IllegalStateException("The upscaled photo result is missing.")
                resultFile = withContext(Dispatchers.IO) {
                    val directory = File(context.filesDir, "photo_ai_upscale")
                    if (!directory.exists() && !directory.mkdirs()) {
                        throw IllegalStateException("Could not create private photo-upscale storage.")
                    }
                    val target = File(directory, "${safeFilename(clipId)}_${UUID.randomUUID()}.png")
                    val temporary = File.createTempFile("photo_upscale_", ".tmp", directory)
                    try {
                        FileOutputStream(temporary).use { stream ->
                            check(outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                "Could not encode the upscaled photo."
                            }
                        }
                        if (!temporary.renameTo(target)) throw IllegalStateException("Could not publish the upscaled photo safely.")
                    } finally {
                        temporary.delete()
                    }
                    target
                }

                withContext(Dispatchers.Main.immediate) {
                    synchronized(this@applyPhotoAiUpscale) {
                        val latest = _state.value
                        val latestClip = latest.project?.clips?.firstOrNull { it.id == clipId }
                        if (photoAiEnhanceJob !== request || !request.isActive) return@synchronized
                        if (!photoAiResultCanCommit(
                                expectedProjectId, clipId, expectedSourceUri, latest.project?.id, latestClip
                            )) {
                            _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(
                                message = "Result discarded because the project photo changed during processing."
                            )
                            return@synchronized
                        }
                        pushUndo()
                        updateClip(clipId) { latestClipNow ->
                            latestClipNow.copy(photoEdit = latestClipNow.photoEdit.copy(
                                aiUpscaledImageUri = resultFile!!.toURI().toString()
                            ))
                        }
                        committed = true
                        _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(
                            progress01 = 1f,
                            message = "Enhanced photo ready (output capped at 4.2 megapixels).",
                            outputUri = resultFile!!.toURI().toString()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                publishPhotoAi(request) {
                    PhotoAiEnhanceUiState(
                        message = "Couldn't enhance this photo. Try another image."
                    )
                }
            } finally {
                if (inferenceBitmap != null && inferenceBitmap !== raw && inferenceBitmap?.isRecycled == false) {
                    inferenceBitmap?.recycle()
                }
                if (raw?.isRecycled == false) raw?.recycle()
                if (enhanced?.isRecycled == false) enhanced?.recycle()
                if (!committed) resultFile?.delete()
                if (photoAiEnhanceJob === request) {
                    photoAiEnhanceJob = null
                    if (!committed && request.isCancelled) {
                        _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(message = "Photo enhancement cancelled.")
                    }
                }
            }
        }
        photoAiEnhanceJob = request
        _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(
            processing = true, message = "Preparing offline photo upscale…"
        )
        request.start()
    }
}

/** Find a face, then apply a crop to the selected image. */
fun EditorViewModel.applyPhotoFaceCrop(clipId: String) {
    val context = context?.applicationContext ?: run {
        _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(message = "The editor is not ready. Try again.")
        return
    }
    synchronized(this) {
        if (photoAiEnhanceJob?.isActive == true) return
        val project = _state.value.project
        val clip = project?.clips?.firstOrNull { it.id == clipId }
        if (project == null || clip?.type != ClipType.IMAGE || clip.uri.isBlank()) {
            _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(message = "Select a photo first.")
            return
        }
        val expectedProjectId = project.id
        val expectedSourceUri = clip.uri
        val expectedSettings = clip.photoEdit
        lateinit var request: Job
        request = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var raw: Bitmap? = null
            var analysisBitmap: Bitmap? = null
            var committed = false
            publishPhotoAi(request) {
                PhotoAiEnhanceUiState(processing = true, message = "Finding a face…")
            }
            try {
                val crop = withContext(Dispatchers.IO) {
                    ImageAiScope.withExistingProjectImage(clip, ProtectedAiAction.FACE_AWARE_REFRAME) {
                        raw = PhotoEditRenderer.loadBitmap(context, expectedSourceUri, 1600)
                            ?: throw IllegalStateException("The selected image could not be decoded.")
                        analysisBitmap = PhotoEditRenderer.prepareForCropAnalysis(raw!!, expectedSettings)
                        val coroutineContext = currentCoroutineContext()
                        val detector = BundledMediaPipeModels.openFace(context, maxFaces = 5) {
                            coroutineContext.ensureActive()
                        }
                        val faces = detector.use { it.detectCancellable(analysisBitmap!!) }
                        val sourceAspect = analysisBitmap!!.width.toFloat() / analysisBitmap!!.height
                        val presetAspect = PhotoEditSettings.aspectRatioForPreset(
                            expectedSettings.cropAspectPreset ?: "free"
                        )
                        PhotoFaceCropEngine.cropAroundLargestFace(
                            analysisBitmap!!.width,
                            analysisBitmap!!.height,
                            faces,
                            outputAspectRatio = presetAspect ?: sourceAspect,
                        )
                    }
                }
                if (crop == null) {
                    publishPhotoAi(request) {
                        PhotoAiEnhanceUiState(message = "No face was detected; the photo was left unchanged.")
                    }
                } else {
                    withContext(Dispatchers.Main.immediate) {
                        synchronized(this@applyPhotoFaceCrop) {
                            val latest = _state.value
                            val latestClip = latest.project?.clips?.firstOrNull { it.id == clipId }
                            if (photoAiEnhanceJob !== request || !request.isActive) return@synchronized
                            if (!photoAiResultCanCommit(
                                    expectedProjectId, clipId, expectedSourceUri, latest.project?.id, latestClip,
                                    expectedPhotoEditSettings = expectedSettings
                                )) {
                                _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(
                                    message = "The selected photo changed. Nothing was changed."
                                )
                                return@synchronized
                            }
                            pushUndo()
                            updateClip(clipId) { current ->
                                current.copy(photoEdit = current.photoEdit.copy(
                                    crop = crop,
                                    // Face crop uses a precise source-pixel aspect. Unlock manual drag
                                    // constraints that would otherwise reinterpret normalized geometry.
                                    cropAspectPreset = "free"
                                ))
                            }
                            committed = true
                            _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(
                                progress01 = 1f,
                                message = "Face crop applied. Preview and export use the same crop."
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                publishPhotoAi(request) {
                    PhotoAiEnhanceUiState(
                        message = "Couldn't find a face in this photo. Try another image."
                    )
                }
            } finally {
                if (analysisBitmap != null && analysisBitmap !== raw && analysisBitmap?.isRecycled == false) {
                    analysisBitmap?.recycle()
                }
                if (raw?.isRecycled == false) raw?.recycle()
                if (photoAiEnhanceJob === request) {
                    photoAiEnhanceJob = null
                    if (!committed && request.isCancelled) {
                        _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(message = "Face crop cancelled.")
                    }
                }
            }
        }
        photoAiEnhanceJob = request
        _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(
            processing = true, message = "Finding a face…"
        )
        request.start()
    }
}

fun EditorViewModel.cancelPhotoAiEnhance() {
    val active = photoAiEnhanceJob
    photoAiEnhanceJob = null
    active?.cancel()
    _photoAiEnhanceUi.value = PhotoAiEnhanceUiState(message = "Photo change cancelled.")
}

private fun EditorViewModel.publishPhotoAi(
    request: Job,
    transform: (PhotoAiEnhanceUiState) -> PhotoAiEnhanceUiState,
) {
    if (photoAiEnhanceJob !== request || !request.isActive) return
    _photoAiEnhanceUi.update(transform)
}

private fun safeFilename(value: String): String = value.map { char ->
    if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_'
}.joinToString("").take(80).ifEmpty { "photo" }
