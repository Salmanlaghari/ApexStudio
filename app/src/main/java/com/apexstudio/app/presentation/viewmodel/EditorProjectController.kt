package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


import com.apexstudio.app.domain.model.timeline.syncSharedTimeline
/**
 * Legacy overlay repair (Bug B): OVERLAY clips created before the
 * overlay-track system (or by a buggy build) can carry trackIndex 0
 * (or outside 1..9) — such a clip renders NOWHERE (the V1 lane only
 * shows VIDEO/IMAGE, the OVL rows only show their own layer), which
 * reads as "overlay missing from the timeline" even though the clip
 * exists. On load, move those clips to the first free overlay layer.
 * In-memory only until the next persist; idempotent.
 */
internal fun normalizeOverlayTrackIndices(clips: List<MediaClip>): List<MediaClip> {
    val used = clips.filter { it.type == ClipType.OVERLAY && it.trackIndex in 1..9 }
        .map { it.trackIndex }.toMutableSet()
    var changed = false
    val out = clips.map { clip ->
        if (clip.type == ClipType.OVERLAY && clip.trackIndex !in 1..9) {
            val layer = (1..9).firstOrNull { it !in used } ?: 1
            used += layer
            changed = true
            clip.copy(trackIndex = layer)
        } else clip
    }
    if (changed) {
        Log.w("EditorViewModel", "Normalized legacy overlay trackIndex values to free overlay layers")
    }
    return out
}

/** New main-track video imports start edge-to-edge; other media paths retain their defaults. */
internal fun withImportedMainVideoFitDefault(clip: MediaClip): MediaClip =
    if (clip.type == ClipType.VIDEO) clip.copy(videoFitMode = VideoFitMode.COVER) else clip


internal fun EditorViewModel.loadProject() {
    viewModelScope.launch {
        // Prefer the persistent DataStore copy over the in-memory
        // MediaRepository stub. The two stay in sync because every
        // mutation auto-saves back to DataStore.
        val projects = projectRepository?.loadAll()?.first() ?: repo.loadProjects()
        val p = resolveEditorProject(projects, projectId)
        if (p == null) {
            _state.update {
                it.copy(
                    project = null,
                    durationMs = 0L,
                    beatMarkersMs = emptyList(),
                    beatMarkersByTrackUri = emptyMap(),
                    beatSourceTrackId = null,
                    beatsAnalyzing = false
                )
            }
            return@launch
        }

        val sampleUri = context?.let { ctx ->
            try {
                com.apexstudio.app.data.media.SampleVideoGenerator.getOrCreateSampleVideo(ctx)
            } catch (_: Exception) { null }
        }
        val ctx = context
        val validClips = p.clips.map { clip ->
            if (ctx != null) {
                val resolvedUri = com.apexstudio.app.data.media.MediaUriResolver.resolvePlayableUri(ctx, clip.uri).toString()
                // Resolution can substitute a sample for a lost source. Never transfer measured
                // face coordinates to different media; a changed alias safely requires re-analysis.
                clip.copy(uri = resolvedUri, faceCropTrack = clip.faceCropTrack.takeIf { resolvedUri == clip.uri })
            } else if (clip.uri.startsWith("asset://") || clip.uri.isBlank() || !clip.uri.contains("/")) {
                if (sampleUri != null) clip.copy(uri = sampleUri, faceCropTrack = null) else clip
            } else clip
        }
        val effectiveClips = if (validClips.isEmpty() && sampleUri != null) {
            listOf(
                MediaClip(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "Sample_V1.mp4",
                    uri = sampleUri,
                    durationMs = 10_000L,
                    trimStartMs = 0L,
                    trimEndMs = 10_000L,
                    type = ClipType.VIDEO,
                    trackIndex = 0
                )
            )
        } else normalizeOverlayTrackIndices(validClips)
        val updatedP = p.copy(
            clips = effectiveClips,
            durationMs = computeTimelineDurationMs(effectiveClips, p.audioTracks),
            hiddenVideoLayers = p.hiddenVideoLayers.filter { it in 0 until MAX_VIDEO_LAYERS }.toSet(),
            lockedVideoLayers = p.lockedVideoLayers.filter { it in 0 until MAX_VIDEO_LAYERS }.toSet()
        ).syncSharedTimeline(
            importLegacyAnimation = true,
            refreshLegacyClipFields = true
        )

        val firstClipId = updatedP.clips.firstOrNull { it.type == ClipType.VIDEO }?.id
            ?: updatedP.clips.firstOrNull()?.id
        _state.update {
            val validSelectedClipIds = it.selectedClipIds.intersect(updatedP.clips.map { clip -> clip.id }.toSet())
            it.copy(
                project = updatedP,
                durationMs = updatedP.durationMs,
                hiddenVideoLayers = updatedP.hiddenVideoLayers,
                lockedVideoLayers = updatedP.lockedVideoLayers,
                canUndo = false, canRedo = false,
                selectedClipId = it.selectedClipId ?: firstClipId,
                selectedClipIds = validSelectedClipIds,
                multiSelectMode = it.multiSelectMode && validSelectedClipIds.isNotEmpty(),
                beatMarkersMs = emptyList(),
                beatMarkersByTrackUri = emptyMap(),
                beatSourceTrackId = null,
                beatsAnalyzing = false,
                snapToBeat = false,
                // Loading a project establishes its initial playhead; it
                // must not start playback before the user presses Play.
                isPlaying = false
            )
        }

        // Audio-list sync (Kilo): the project list is the source of
        // truth. The mixer list (_audio.tracks) must carry the same
        // tracks, or the dual-write setters silently no-op for tracks
        // missing from one list (diverged / old project). The old
        // loadAudioState() read the FIRST project from the stub repo —
        // the wrong project when several exist — and is deleted.
        val mixerIds = _audio.value.tracks.map { it.id }.toSet()
        val projectIds = updatedP.audioTracks.map { it.id }.toSet()
        if (mixerIds != projectIds) {
            Log.w(
                "EditorViewModel",
                "Audio track lists diverged on load (mixer=${mixerIds.size}, project=${projectIds.size}) — syncing mixer from project"
            )
        }
        _audio.update { it.copy(tracks = updatedP.audioTracks) }

        // Reclaim orphaned project_media copies (Kilo): files in
        // filesDir/project_media/ not referenced by any saved
        // project (deleted projects, failed persists, pre-cleanup
        // builds). URIs are the SAVED ones (pre-resolution), so
        // persisted copies match.
        if (ctx != null) {
            val activeUris = projects.flatMap { proj ->
                proj.clips.map { it.uri } + proj.audioTracks.map { it.uri }
            }
            withContext(Dispatchers.IO) {
                com.apexstudio.app.data.picker.cleanupStalePersistedMedia(ctx, activeUris)
            }
        }

        // Auto-apply the project's last transmission template so
        // the user opens to the look they had last time. Runs
        // after [loadTransmissionTemplates] because we need the
        // catalog to be in the StateFlow before we can resolve
        // the template by id. Falls back silently if the saved
        // id no longer matches a live template (e.g. the
        // template was removed from the catalog).
        val savedTemplateId = updatedP.lastTransmissionTemplateId
        if (savedTemplateId != null) {
            val template = _transmissionTemplates.value.firstOrNull { it.id == savedTemplateId }
            if (template != null) {
                applyTransmissionTemplateInternal(template, persistProjectId = null)
            } else {
                Log.w("EditorViewModel", "Saved transmission template '$savedTemplateId' not found in catalog — skipping auto-apply")
            }
        }
    }
}


internal fun EditorViewModel.loadLuts() {
    _luts.value = repo.loadLutPresets()
    _transitions.value = repo.loadTransitionPresets()
    _fx.value = repo.loadFxPresets()
}


internal fun EditorViewModel.loadTransmissionTemplates() {
    val mgr = timelineTemplateManager ?: run {
        Log.w("EditorViewModel", "TimelineTemplateManager unavailable — transmission catalog empty")
        return
    }
    try {
        val raw = mgr.loadTransmissionTemplatesRaw()
        val stats = mgr.parseTransmissionTemplatesWithStats(raw)
        _transmissionTemplates.value = stats.templates
        if (stats.skippedCount > 0) {
            Log.w(
                "EditorViewModel",
                "transmission_templates.json: ${stats.skippedCount} entries skipped " +
                    "(unknown LUT or FX id) — ids: ${stats.skippedIds.joinToString()}"
            )
        }
    } catch (e: Exception) {
        Log.w("EditorViewModel", "loadTransmissionTemplates failed", e)
        _transmissionTemplates.value = emptyList()
    }
}


fun EditorViewModel.onMediaPicked(mediaList: List<com.apexstudio.app.data.picker.MediaMetadata>, replace: Boolean = false) {
    viewModelScope.launch {
        val s = _state.value
        // Phase D: read the pendingAddAsOverlay flag from state so
        // the picker's callback can route the result through either
        // the regular video path or the overlay path. Audio picks
        // (pendingAddAsAudio) are routed into project.audioTracks so
        // they land on the A1 lane — previously the flag was consumed
        // by nothing and audio became invisible ClipType.AUDIO clips
        // that no timeline lane renders.
        val asOverlay = s.pendingAddAsOverlay
        val asAudio = s.pendingAddAsAudio
        val targetLayer = s.pendingAddToLayer?.coerceIn(1, MAX_VIDEO_LAYERS - 1)
        if (asAudio && !replace) {
            mediaList.forEach { meta ->
                val uri = if (context != null) {
                    com.apexstudio.app.data.media.MediaUriResolver.resolvePlayableUri(context!!, meta.uri).toString()
                } else meta.uri
                addAudioTrack(
                    name = meta.name.ifBlank { "Imported Audio" },
                    uri = uri,
                    kind = com.apexstudio.app.domain.model.AudioTrack.Kind.MUSIC,
                    sourceDurationMs = meta.durationMs
                )
            }
            _state.update {
                it.copy(
                    pickedMedia = mediaList,
                    isMediaPickerOpen = false,
                    pendingAddAsOverlay = false,
                    pendingAddAsAudio = false
                )
            }
            return@launch
        }
        val playheadMs = s.playerPositionMs.coerceAtLeast(0L)
        // First free overlay layer for this batch (multi-pick spreads
        // across consecutive free layers instead of stacking all clips
        // on one track at the same offset).
        val firstFreeLayer = nextFreeVideoLayer()
            .takeIf { it in 1 until MAX_VIDEO_LAYERS } ?: 1
        val newClips = mutableListOf<com.apexstudio.app.domain.model.MediaClip>()
        for ((batchIndex, meta) in mediaList.withIndex()) {
            val resolvedMeta = if (context != null) {
                meta.copy(uri = com.apexstudio.app.data.media.MediaUriResolver.resolvePlayableUri(context!!, meta.uri).toString())
            } else meta
            // Overlay (PiP) add path: the picked media becomes an
            // OVERLAY clip on a dedicated overlay track at the
            // playhead — NOT at 00:00 (the old behaviour, which made
            // new overlays invisible whenever the playhead was past
            // their span). Images get a 5s default (CapCut-style);
            // videos keep their source length. The intrinsic aspect
            // is stored so the preview box and the export PiP box
            // never stretch the media into the legacy 16:9 box.
            // Video overlays are audible at unity gain; image
            // overlays have no audio (volume ignored).
            val isOverlayPick = targetLayer != null || asOverlay
            // Overlay media is persisted into app-private storage
            // BEFORE the clip is created: a picked content:// URI
            // carries only a grant-scoped read permission, so a saved
            // project referencing the original URI can go unreadable
            // after process death — the preview then shows a black
            // box and the overlay is uneditable. The copy is
            // best-effort (falls back to the original URI) and runs
            // on IO so the add path never blocks the UI thread.
            val effectiveMeta = if (isOverlayPick && context != null) {
                val persisted = com.apexstudio.app.data.picker.persistPickedMediaToAppStorage(
                    context!!, resolvedMeta.uri, resolvedMeta.name
                )
                resolvedMeta.copy(uri = persisted)
            } else resolvedMeta
            val created = mediaPicker?.toMediaClip(effectiveMeta, s.project?.clips?.size ?: 0)
            if (isOverlayPick && created != null) {
                val isImage = resolvedMeta.type == ClipType.IMAGE
                val overlayDurationMs = if (isImage) 5_000L else created.durationMs.coerceAtLeast(500L)
                val aspect = if (resolvedMeta.width > 0 && resolvedMeta.height > 0) {
                    (resolvedMeta.width.toFloat() / resolvedMeta.height.toFloat())
                        .coerceIn(0.1f, 10f)
                } else 16f / 9f
                // First free overlay layer (OVL1 = trackIndex 1, ...),
                // or the explicitly requested layer from the V2 "+".
                // Multi-pick spreads across consecutive free layers.
                val layer = targetLayer
                    ?: (firstFreeLayer + batchIndex).coerceAtMost(MAX_VIDEO_LAYERS - 1)
                newClips += created.copy(
                    type = ClipType.OVERLAY,
                    trackIndex = layer,
                    timelineOffsetMs = playheadMs,
                    durationMs = overlayDurationMs,
                    trimStartMs = 0L,
                    trimEndMs = overlayDurationMs,
                    overlayAspect = aspect,
                    overlayVolume = if (isImage) 0f else 1f
                )
            } else if (created != null) {
                // Main-track videos start edge-to-edge in the composition frame.
                // Overlay media keeps its own aspect/box behavior; saved projects
                // retain their FIT default unless the user changes it.
                newClips += withImportedMainVideoFitDefault(created)
            }
        }
        val existingClips = if (replace) emptyList() else (s.project?.clips ?: emptyList())

        val updatedClips = existingClips + newClips
        val updatedProject = s.project?.copy(clips = updatedClips)
        // Unified duration (was MAX of raw durations -> playhead raced
        // on multi-clip timelines). A fresh clip always plays at 1x, so
        // drop any stale global speed left over from another clip.
        val maxDuration = computeTimelineDurationMs(updatedClips, s.project?.audioTracks ?: emptyList())

        // Immediate state update so video preview loads in 0ms without delay
        // Undo: adding clips is undoable — snapshot BEFORE the mutation.
        if (newClips.isNotEmpty()) pushUndo()
        _state.update {
            it.copy(
                project = updatedProject,
                durationMs = maxDuration,
                pickedMedia = mediaList,
                isMediaPickerOpen = false,
                selectedClipId = when {
                    // Overlay picks select the new overlay (so the clip
                    // bar + properties panel target it, not the old clip).
                    asOverlay || targetLayer != null -> newClips.firstOrNull()?.id
                    replace -> newClips.firstOrNull()?.id
                    it.selectedClipId != null -> it.selectedClipId
                    else -> newClips.firstOrNull()?.id
                },
                isPlaying = true,
                // Phase D: register the freshly added overlay so the
                // preview layer starts rendering it immediately, and
                // select it on the PiP canvas so its edit handles show.
                // Multi-layer: a clip routed to a specific layer also
                // registers as the active overlay for instant preview.
                overlayClipId = if (asOverlay || targetLayer != null) {
                    newClips.firstOrNull()?.id ?: it.overlayClipId
                } else it.overlayClipId,
                selectedOverlayClipId = if (asOverlay || targetLayer != null) {
                    newClips.firstOrNull()?.id ?: it.selectedOverlayClipId
                } else it.selectedOverlayClipId,
                overlayTransform = if (asOverlay || targetLayer != null) {
                    com.apexstudio.app.presentation.state.OverlayTransform.Identity
                } else it.overlayTransform,
                pendingAddAsOverlay = false,
                pendingAddAsAudio = false,
                pendingAddToLayer = null,
                playbackSpeed = 1f,
                // The layer now holds clips: drop it from the empty-layer set.
                extraVideoLayers = if (targetLayer != null) it.extraVideoLayers - targetLayer else it.extraVideoLayers
            )
        }
        persistProject()

        val firstVideo = updatedClips.firstOrNull { it.type == ClipType.VIDEO } ?: updatedClips.firstOrNull()
        // Asynchronously analyze audio waveform in background without blocking video preview
        if (firstVideo != null && context != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val wf = mediaAnalyzer?.analyzeAudioWaveform(
                        firstVideo.uri, context,
                        sampleCount = 200,
                        trimStartMs = firstVideo.trimStartMs,
                        trimEndMs = firstVideo.trimEndMs
                    )?.samples ?: FloatArray(0)
                    if (wf.isNotEmpty()) {
                        _state.update { it.copy(audioWaveform = wf) }
                    }
                } catch (e: Exception) {
                    Log.w("EditorViewModel", "Waveform analysis failed", e)
                }
            }
            // Auto-detect source video aspect ratio (16:9, 9:16, 1:1, 4:5, etc.)
            // so preview + export match the source without manual setup.
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val ratio = detectVideoAspectRatio(context!!, firstVideo.uri)
                    if (ratio != null && ratio > 0) {
                        _state.update { st ->
                            val proj = st.project
                            if (proj != null && proj.videoAspectRatio == null) {
                                st.copy(project = proj.copy(videoAspectRatio = ratio))
                            } else st
                        }
                        persistProject()
                        Log.d("EditorViewModel", "Auto-detected video aspect ratio: $ratio")
                    }
                } catch (e: Exception) {
                    Log.w("EditorViewModel", "Aspect ratio detection failed", e)
                }
            }
        }

        for (clip in newClips) {
            if (clip.type == ClipType.VIDEO && context != null) {
                loadClipThumbnails(clip)
            }
        }
    }
}


/**
 * One resident decoded strip: the frames themselves plus the key they
 * were decoded for, so re-trimming a clip is detected and re-decoded
 * instead of serving frames from the old trim window.
 */
private class ClipThumbStrip(
    val storeKey: String,
    val frames: List<Pair<Long, android.graphics.Bitmap>>
)

/**
 * Bounded LRU of decoded per-clip thumbnail strips (Bug 1).
 *
 * Replaces an unbounded `Map` on `_thumbnails` that a new clip was only
 * ever ADDED to, so bitmaps of deleted clips stayed pinned for the life
 * of the process. Eviction order here is by last update (entries are
 * written from `Dispatchers.IO` on load, never read through `get`);
 * 40 clips is far past a realistic project while still guaranteeing the
 * map cannot grow without limit.
 */
private val clipThumbStrips =
    com.apexstudio.editor.thumbnail.ThumbnailStripLru<String, ClipThumbStrip>(40)

/**
 * Extract the thumbnail strip for [clip] off the main thread and mirror
 * it into `_thumbnails` for the UI.
 *
 * Bug 1 (timeline filmstrip blank after the first few seconds):
 * the frame count is now derived from THIS CLIP'S OWN DURATION —
 * `ThumbnailSequenceGenerator.sampleCount`, one frame per second
 * clamped to [8, 300] — where it used to be a hard-coded `count = 8`.
 * A 3-minute video therefore yields 180 frames covering 0..3:00 instead
 * of 8 frames, and the video layer stays filled for the whole clip.
 *
 * Nothing here blocks the UI: the decode runs on `Dispatchers.IO`, the
 * result is served from the LRU when the same trim is loaded again, and
 * strips belonging to clips that have left the project are pruned on
 * every publish so state cannot grow without bound.
 */
internal fun EditorViewModel.loadClipThumbnails(clip: MediaClip) {
    val ctx = context ?: return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val playableUri = com.apexstudio.app.data.media.MediaUriResolver
                .resolvePlayableUri(ctx, clip.uri).toString()
            val durationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1000L)
            val frameCount = com.apexstudio.editor.thumbnail.ThumbnailSequenceGenerator
                .sampleCount(durationMs)
            val storeKey = "${clip.id}|${clip.trimStartMs}|${clip.trimEndMs}|$frameCount"
            val cached = clipThumbStrips.peek(clip.id)
            if (cached == null || cached.storeKey != storeKey) {
                val decoded = VideoThumbnailExtractor.extractStrip(
                    context = ctx,
                    videoUri = playableUri,
                    durationMs = durationMs,
                    count = frameCount
                )
                clipThumbStrips.put(clip.id, ClipThumbStrip(storeKey, decoded))
            }
            // Drop strips for clips the user has deleted — this, not the
            // cap, is what an unbounded map could never do.
            val liveIds = _state.value.project?.clips?.mapTo(HashSet<String>()) { it.id }
                ?: HashSet<String>()
            if (liveIds.isNotEmpty()) {
                clipThumbStrips.keys().filter { it !in liveIds }
                    .forEach { clipThumbStrips.remove(it) }
            }
            _thumbnails.update {
                clipThumbStrips.snapshot().mapValues { (_, entry) -> entry.frames }
            }
        } catch (e: Exception) {
            Log.e("EditorViewModel", "Thumbnail extraction failed for ${clip.id}", e)
        }
    }
}


fun EditorViewModel.flushProject() = persistProject()


internal fun EditorViewModel.persistProject(
    importLegacyAnimation: Boolean = false,
    refreshLegacyClipFields: Boolean = false,
    refreshLegacyTextStickerFields: Boolean = false,
    refreshLegacyDrawingClipIds: Set<String> = emptySet()
) {
    val snapshot = _state.value.project ?: return
    val repo = projectRepository ?: return
    // Phase 3: stamp edit time so History sorts correctly.
    val stamped = snapshot.syncSharedTimeline(
        importLegacyAnimation = importLegacyAnimation,
        refreshLegacyClipFields = refreshLegacyClipFields,
        refreshLegacyTextStickerFields = refreshLegacyTextStickerFields,
        refreshLegacyDrawingClipIds = refreshLegacyDrawingClipIds
    ).copy(lastEditedMs = System.currentTimeMillis())
    if (stamped != snapshot) {
        _state.update { it.copy(project = stamped) }
    }
    viewModelScope.launch {
        try {
            repo.saveProject(stamped)
            markAutoSaved()
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Auto-save failed", e)
        }
    }
}

/**
 * Phase 3: saves the current project as a named draft.
 * Drafts appear in the Drafts tab and can be resumed/deleted.
 */
fun EditorViewModel.saveDraft(name: String? = null) {
    val snapshot = _state.value.project ?: return
    val repo = projectRepository ?: return
    val draft = snapshot.syncSharedTimeline().copy(
        name = name?.takeIf { it.isNotBlank() } ?: snapshot.name,
        isDraft = true,
        lastEditedMs = System.currentTimeMillis()
    )
    _state.update { it.copy(project = draft) }
    viewModelScope.launch {
        try {
            repo.saveProject(draft)
            markAutoSaved()
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Draft save failed", e)
        }
    }
}

/** Phase 3: deletes a project/draft by id. */
fun EditorViewModel.deleteSavedProject(projectId: String) {
    val repo = projectRepository ?: return
    viewModelScope.launch {
        try {
            // Snapshot the deleted project's URIs BEFORE deleting it,
            // minus URIs still referenced by other projects (shared
            // media must survive), so its persisted project_media
            // copies can be reclaimed (Kilo) without breaking others.
            var ownedUris: List<String> = emptyList()
            val ctx = context
            if (ctx != null) {
                try {
                    val all = repo.loadAll().first()
                    val deleted = all.firstOrNull { it.id == projectId }
                    if (deleted != null) {
                        val others = all.filter { it.id != projectId }
                            .flatMap { it.clips.map { c -> c.uri } + it.audioTracks.map { t -> t.uri } }
                            .toSet()
                        ownedUris = (deleted.clips.map { it.uri } + deleted.audioTracks.map { it.uri })
                            .filter { it !in others }
                    }
                } catch (e: Exception) {
                    Log.w("EditorViewModel", "Persisted-media snapshot on delete failed", e)
                }
            }
            repo.deleteProject(projectId)
            if (ctx != null && ownedUris.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    com.apexstudio.app.data.picker.deletePersistedMediaForUris(ctx, ownedUris)
                }
            }
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Delete failed", e)
        }
    }
}

/** Phase 3: opens a saved project/draft in the editor. */
fun EditorViewModel.openSavedProject(project: com.apexstudio.app.domain.model.Project) {
    _state.update {
        it.copy(project = project.copy(clips = normalizeOverlayTrackIndices(project.clips)).syncSharedTimeline(
            importLegacyAnimation = true,
            refreshLegacyClipFields = true
        ))
    }
    persistProject()
}


fun EditorViewModel.analyzeAudio(uri: String) {
    viewModelScope.launch {
        val data = mediaAnalyzer?.analyzeAudioWaveform(uri, context ?: return@launch)
        data?.samples?.let { setWaveformSamples(it) }
    }
}


/**
 * Detects the source video's aspect ratio (width / height) using
 * MediaMetadataRetriever. Returns null if detection fails.
 * Handles rotation metadata (90/270° swaps width/height).
 */
internal fun detectVideoAspectRatio(context: android.content.Context, uri: String): Float? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, android.net.Uri.parse(uri))
        val w = retriever.extractMetadata(
            android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(
            android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(
            android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )?.toIntOrNull() ?: 0
        if (w > 0 && h > 0) {
            // 90° or 270° rotation swaps dimensions.
            val (ew, eh) = if (rotation == 90 || rotation == 270) h to w else w to h
            ew.toFloat() / eh.toFloat()
        } else null
    } catch (e: Exception) {
        android.util.Log.w("EditorViewModel", "detectVideoAspectRatio failed for $uri", e)
        null
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}
