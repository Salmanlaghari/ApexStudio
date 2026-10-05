package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


fun EditorViewModel.openFilterPanel() {
    openGpuFilterPanel()
}

fun EditorViewModel.closeFilterPanel() = closeGpuFilterPanel()


// Real-Time GPUImage Video Filtering Studio
fun EditorViewModel.openGpuFilterPanel() {
    val selectedClip = _state.value.selectedClipId?.let { id ->
        _state.value.project?.clips?.firstOrNull { it.id == id }
    } ?: _state.value.project?.clips?.firstOrNull()

    val clipConfig = selectedClip?.gpuFilterConfig ?: com.apexstudio.app.data.filter.GpuFilterConfig()
    _state.update {
        it.copy(
            gpuFilterPanelOpen = true,
            activeGpuFilterConfig = clipConfig,
            filterPanelOpen = false,
            colorGradingLutPanelOpen = false
        )
    }
    ensureFilterThumbnails()
}


fun EditorViewModel.closeGpuFilterPanel() = _state.update { it.copy(gpuFilterPanelOpen = false) }


fun EditorViewModel.setGpuFilterConfig(config: com.apexstudio.app.data.filter.GpuFilterConfig) {
    _state.update { state ->
        val updatedClips = state.project?.clips?.map { clip ->
            if (clip.id == state.selectedClipId) {
                clip.copy(gpuFilterConfig = config)
            } else clip
        } ?: emptyList()

        state.copy(
            activeGpuFilterConfig = config,
            activeFilterId = config.filterPresetId,
            filterIntensity = config.filterIntensity,
            project = state.project?.copy(clips = updatedClips)
        )
    }
}


fun EditorViewModel.applyColorProfilePreset(profileId: String, intensity: Float = 1.0f) {
    val profile = com.apexstudio.app.data.filter.GpuColorProfiles.findById(profileId)
    if (profile != null) {
        setGpuFilterConfig(profile.applyWithIntensity(intensity))
    }
}


fun EditorViewModel.toggleGpuFilterCompareMode() = _state.update { it.copy(gpuFilterCompareMode = !it.gpuFilterCompareMode) }


fun EditorViewModel.setGpuFilterSplitPosition(split: Float) = _state.update {
    it.copy(gpuFilterSplitPosition = split.coerceIn(0.05f, 0.95f))
}


fun EditorViewModel.setGpuFilterSelectedTab(tab: Int) = _state.update { it.copy(gpuFilterSelectedTab = tab) }


fun EditorViewModel.applyGpuFilterToSelectedClip() {
    val activeConfig = _state.value.activeGpuFilterConfig
    val selectedId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id
    if (selectedId != null) {
        _state.update { state ->
            val updatedClips = state.project?.clips?.map { clip ->
                if (clip.id == selectedId) clip.copy(gpuFilterConfig = activeConfig) else clip
            } ?: emptyList()
            state.copy(
                project = state.project?.copy(clips = updatedClips),
                gpuFilterPanelOpen = false
            )
        }
    } else {
        closeGpuFilterPanel()
    }
}


fun EditorViewModel.applyGpuFilterToAllClips() {
    val activeConfig = _state.value.activeGpuFilterConfig
    _state.update { state ->
        val updatedClips = state.project?.clips?.map { clip ->
            clip.copy(gpuFilterConfig = activeConfig)
        } ?: emptyList()
        state.copy(
            project = state.project?.copy(clips = updatedClips),
            gpuFilterPanelOpen = false
        )
    }
}


fun EditorViewModel.resetGpuFilter() {
    val defaultConfig = com.apexstudio.app.data.filter.GpuFilterConfig()
    setGpuFilterConfig(defaultConfig)
}


fun EditorViewModel.setFilterCategory(id: String) = _state.update { it.copy(filterCategory = id) }

fun EditorViewModel.setActiveFilter(id: String?) = _state.update { it.copy(activeFilterId = id) }

fun EditorViewModel.setFilterIntensity(v: Float) = _state.update { it.copy(filterIntensity = v.coerceIn(0f, 1f)) }


// Professional Color Grading LUTs Panel (GPUImage-powered)
fun EditorViewModel.openColorGradingLutPanel() {
    _state.update { it.copy(colorGradingLutPanelOpen = true) }
    ensureFilterThumbnails()
}

fun EditorViewModel.closeColorGradingLutPanel() = _state.update { it.copy(colorGradingLutPanelOpen = false) }

fun EditorViewModel.setLutTargetTrack(target: LutTargetTrack) = _state.update { it.copy(lutTargetTrack = target) }

fun EditorViewModel.toggleLutFavorite(lutId: String) = _state.update {
    val current = it.lutFavoriteIds
    val updated = if (current.contains(lutId)) current - lutId else current + lutId
    it.copy(lutFavoriteIds = updated)
}

fun EditorViewModel.toggleLutCompareMode() = _state.update { it.copy(lutCompareMode = !it.lutCompareMode) }

fun EditorViewModel.setLutCompareSplitPosition(split: Float) = _state.update {
    it.copy(lutCompareSplitPosition = split.coerceIn(0f, 1f))
}

fun EditorViewModel.setLutContrast(value: Float) = _state.update { it.copy(lutContrast = value.coerceIn(0.5f, 2.0f)) }

fun EditorViewModel.setLutSaturation(value: Float) = _state.update { it.copy(lutSaturation = value.coerceIn(0f, 2.0f)) }

fun EditorViewModel.setLutTemperature(value: Float) = _state.update { it.copy(lutTemperature = value.coerceIn(2000f, 9000f)) }

fun EditorViewModel.setLutTint(value: Float) = _state.update { it.copy(lutTint = value.coerceIn(-100f, 100f)) }

fun EditorViewModel.resetLutGrading() = _state.update {
    it.copy(
        activeFilterId = null,
        filterIntensity = 1.0f,
        lutContrast = 1.0f,
        lutSaturation = 1.0f,
        lutTemperature = 5000f,
        lutTint = 0f,
        lutCompareMode = false
    )
}

fun EditorViewModel.importCustomCubeLut(name: String, inputStream: java.io.InputStream): com.apexstudio.app.data.filter.FilterPreset? {
    val ctx = context ?: return null
    val engine = com.apexstudio.app.data.filter.GpuImageLutEngine(ctx)
    val preset = engine.importCustomCube(name, inputStream)
    if (preset != null) {
        _state.update { it.copy(customImportedLuts = it.customImportedLuts + preset) }
        setActiveFilter(preset.id)
    }
    return preset
}


fun EditorViewModel.setLutGalleryViewMode(mode: com.apexstudio.app.presentation.state.LutGalleryViewMode) = _state.update {
    it.copy(lutGalleryViewMode = mode)
}


fun EditorViewModel.openArFilterPanel() = _state.update { it.copy(arFilterPanelOpen = true) }

fun EditorViewModel.closeArFilterPanel() = _state.update { it.copy(arFilterPanelOpen = false) }

fun EditorViewModel.selectArFilter(id: String?, intensity: Float = 0.85f) = _state.update {
    it.copy(activeArFilterId = id, arFilterIntensity = intensity.coerceIn(0f, 1f))
}

fun EditorViewModel.setArFilterIntensity(v: Float) = _state.update { it.copy(arFilterIntensity = v.coerceIn(0f, 1f)) }

fun EditorViewModel.setArFilterCustomText(text: String) = _state.update { it.copy(arFilterCustomText = text) }


fun EditorViewModel.ensureFilterThumbnails() {
    refreshFilterThumbnailsFromVideo(force = false)
}


fun EditorViewModel.refreshFilterThumbnailsFromVideo(force: Boolean = true) {
    val ctx = context ?: return
    if (!force && (_state.value.filterThumbnails.isNotEmpty() || _state.value.filterThumbnailsLoading)) return
    val manifest = try {
        com.apexstudio.app.data.filter.LutFilterEngine(ctx).manifest
    } catch (e: Exception) {
        Log.w("EditorViewModel", "Failed to load filter manifest", e)
        return
    }
    _state.update { it.copy(filterThumbnailsLoading = true) }
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val currentTime = _state.value.currentTimeMs
            val activeClip = _state.value.selectedClipId?.let { id ->
                _state.value.project?.clips?.firstOrNull { it.id == id }
            } ?: _state.value.project?.clips?.firstOrNull()

            var frameBitmap: android.graphics.Bitmap? = null
            if (activeClip != null && activeClip.uri.isNotBlank()) {
                try {
                    val playableUri = com.apexstudio.app.data.media.MediaUriResolver
                        .resolvePlayableUri(ctx, activeClip.uri).toString()
                    val clipRelativeTime = (currentTime - activeClip.timelineOffsetMs + activeClip.trimStartMs)
                        .coerceIn(activeClip.trimStartMs, activeClip.trimEndMs)
                    frameBitmap = com.apexstudio.app.data.media.VideoThumbnailExtractor
                        .extractFrame(ctx, playableUri, clipRelativeTime)
                } catch (e: Exception) {
                    Log.w("EditorViewModel", "Failed to extract active video frame at playhead", e)
                }
            }

            if (frameBitmap == null && activeClip != null) {
                frameBitmap = _thumbnails.value[activeClip.id]?.firstOrNull()?.second
            }

            val baseFrame = frameBitmap ?: com.apexstudio.app.data.filter.FilterThumbnailGenerator.createGenericPreviewBitmap(ctx)

            val composeMap = com.apexstudio.app.data.filter.FilterThumbnailGenerator
                .generateDynamicThumbnails(ctx, baseFrame, manifest, _state.value.customImportedLuts)

            _state.update {
                it.copy(
                    filterThumbnails = composeMap,
                    filterThumbnailsLoading = false
                )
            }
            Log.d("EditorViewModel", "Generated ${composeMap.size} video-frame filter thumbnails")
        } catch (e: Exception) {
            Log.e("EditorViewModel", "Video frame filter thumbnail generation failed", e)
            _state.update { it.copy(filterThumbnailsLoading = false) }
        }
    }
}


fun EditorViewModel.generateFilterThumbnails(sourceFrame: android.graphics.Bitmap) {
    val ctx = context ?: return
    val manifest = try {
        com.apexstudio.app.data.filter.LutFilterEngine(ctx).manifest
    } catch (e: Exception) {
        Log.w("EditorViewModel", "Failed to load filter manifest", e)
        return
    }
    _state.update { it.copy(filterThumbnailsLoading = true) }
    viewModelScope.launch {
        try {
            val composeMap = com.apexstudio.app.data.filter.FilterThumbnailGenerator
                .generateDynamicThumbnails(ctx, sourceFrame, manifest)
            _state.update {
                it.copy(
                    filterThumbnails = composeMap,
                    filterThumbnailsLoading = false
                )
            }
            Log.d("EditorViewModel", "Generated ${composeMap.size} dynamic filter thumbnails")
        } catch (e: Exception) {
            Log.e("EditorViewModel", "Filter thumbnail generation failed", e)
            _state.update { it.copy(filterThumbnailsLoading = false) }
        }
    }
}


fun EditorViewModel.openFxPanel() = _state.update { it.copy(fxPanelOpen = true) }

fun EditorViewModel.closeFxPanel() = _state.update { it.copy(fxPanelOpen = false) }

fun EditorViewModel.setActiveFx(id: String?) = _state.update { it.copy(activeFxId = id) }

fun EditorViewModel.setFxIntensity(v: Float) = _state.update { it.copy(fxIntensity = v.coerceIn(0f, 1f)) }

fun EditorViewModel.selectFx(id: String?, intensity: Float = 0.85f) = _state.update {
    it.copy(activeFxId = id, fxIntensity = intensity.coerceIn(0f, 1f))
}

fun EditorViewModel.selectFilter(id: String?, intensity: Float = 0.85f) = _state.update {
    it.copy(activeFilterId = id, filterIntensity = intensity.coerceIn(0f, 1f))
}


fun EditorViewModel.updateColorBrightness(v: Float) = _color.update { it.copy(brightness = v) }

fun EditorViewModel.updateColorContrast(v: Float) = _color.update { it.copy(contrast = v.coerceIn(0f, 3f)) }

fun EditorViewModel.updateColorSaturation(v: Float) = _color.update { it.copy(saturation = v.coerceIn(0f, 3f)) }

fun EditorViewModel.updateColorShadows(v: Float) = _color.update { it.copy(shadows = v) }

fun EditorViewModel.updateColorMidtones(v: Float) = _color.update { it.copy(midtones = v) }

fun EditorViewModel.updateColorHighlights(v: Float) = _color.update { it.copy(highlights = v) }

fun EditorViewModel.setColorChannel(c: ColorToolState.Channel) = _color.update { it.copy(activeChannel = c) }

fun EditorViewModel.selectLut(id: String) = _color.update { it.copy(selectedLut = id) }


fun EditorViewModel.applyBrightness(v: Float) { colorGradingEngine.applyBrightness(v) }

fun EditorViewModel.applyContrast(v: Float) { colorGradingEngine.applyContrast(v) }

fun EditorViewModel.applySaturation(v: Float) { colorGradingEngine.applySaturation(v) }

fun EditorViewModel.applyShadows(v: Float) { colorGradingEngine.applyShadows(v) }

fun EditorViewModel.applyMidtones(v: Float) { colorGradingEngine.applyMidtones(v) }

fun EditorViewModel.applyHighlights(v: Float) { colorGradingEngine.applyHighlights(v) }

fun EditorViewModel.applyLut(lutId: String) { colorGradingEngine.applyLut(lutId) }
