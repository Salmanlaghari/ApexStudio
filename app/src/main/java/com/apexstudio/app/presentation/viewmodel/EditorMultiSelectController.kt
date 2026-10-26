package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Atomic actions for the timeline multi-selection. Every public action below
 * creates at most one undo snapshot and one project persistence. */

private fun EditorViewModel.selectedClips(ids: Set<String>): List<MediaClip> =
    _state.value.project?.clips?.filter { it.id in ids }.orEmpty()

fun EditorViewModel.deleteSelectedClips(): Boolean {
    val ids = _state.value.selectedClipIds
    if (ids.isEmpty()) return false
    val project = _state.value.project ?: return false
    val targets = project.clips.filter { it.id in ids }
    if (targets.isEmpty()) return false
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        // Process V1 removals from right to left so each original V1 boundary
        // remains stable while overlays are rippled left exactly once.
        val rippled = targets.filter { isMainTrack(it) }
            .sortedByDescending { v1StartBefore(current.clips, it.id) }
            .fold(current.clips) { clips, clip ->
                rippleOverlays(clips, v1StartBefore(clips, clip.id), -clip.effectiveDurationMs())
            }
        val updated = rippled.filterNot { it.id in ids }
        val duration = computeTimelineDurationMs(updated, current.audioTracks)
        state.copy(
            project = current.copy(clips = updated, durationMs = duration),
            durationMs = duration,
            selectedClipIds = emptySet(),
            multiSelectMode = false,
            selectedClipId = state.selectedClipId?.takeUnless { it in ids }
                ?: updated.firstOrNull()?.id,
            selectedOverlayClipId = state.selectedOverlayClipId?.takeUnless { it in ids },
            overlayClipId = state.overlayClipId?.takeUnless { it in ids },
            overlayTransform = if (state.overlayClipId in ids)
                com.apexstudio.app.presentation.state.OverlayTransform.Identity
            else state.overlayTransform
        )
    }
    persistProject(refreshLegacyClipFields = true, refreshLegacyTextStickerFields = true)
    return true
}

/** Duplicates every selected clip, preserving V1 ripple semantics and selecting
 * the new copies. Copies of overlays are placed after their source. */
fun EditorViewModel.duplicateSelectedClips(): Boolean {
    val ids = _state.value.selectedClipIds
    val project = _state.value.project ?: return false
    val targets = project.clips.filter { it.id in ids }
    if (targets.isEmpty()) return false
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        val mainTargets = targets.filter(::isMainTrack)
        val appendAnchor = current.clips.sumOf { if (isMainTrack(it)) it.effectiveDurationMs() else 0L }
        val extraMainDuration = mainTargets.sumOf { it.effectiveDurationMs() }
        // V1 copies are appended exactly like duplicateClip(). Ripple overlays
        // once at the current V1 end, rather than once at each source start.
        val rippled = if (extraMainDuration > 0L) {
            rippleOverlays(current.clips, appendAnchor, extraMainDuration)
        } else current.clips
        val selectedIds = targets.map { it.id }.toSet()
        val copiedGroupIds = targets.filter { it.groupId != null }
            .groupBy { it.groupId!! }
            .mapValues { (_, members) ->
                val wholeGroupSelected = current.clips.count { it.groupId == members.first().groupId } == members.size && members.all { it.id in selectedIds }
                if (wholeGroupSelected) "group_${UUID.randomUUID()}" else null
            }
        val copies = targets.map { target ->
            val copyId = "clip_${UUID.randomUUID()}_dup"
            target.copy(
                id = copyId,
                groupId = target.groupId?.let { copiedGroupIds[it] },
                timelineOffsetMs = if (target.type == com.apexstudio.app.domain.model.ClipType.OVERLAY) {
                    target.timelineOffsetMs + target.effectiveDurationMs()
                } else target.timelineOffsetMs,
                // Child ids are persisted independently and must not collide
                // with the source or with another copy.
                textOverlays = target.textOverlays.map { it.copy(id = "text_${UUID.randomUUID()}") },
                stickers = target.stickers.map { it.copy(id = "sticker_${UUID.randomUUID()}") },
            )
        }
        val updated = rippled + copies
        val duration = computeTimelineDurationMs(updated, current.audioTracks)
        state.copy(
            project = current.copy(clips = updated, durationMs = duration),
            durationMs = duration,
            selectedClipIds = copies.map { it.id }.toSet(),
            multiSelectMode = true,
            selectedClipId = copies.lastOrNull()?.id
        )
    }
    persistProject(
        importLegacyAnimation = true,
        refreshLegacyClipFields = true,
        refreshLegacyTextStickerFields = true
    )
    return true
}

fun EditorViewModel.groupSelectedClips(): Boolean {
    val ids = _state.value.selectedClipIds
    if (ids.size < 2 || selectedClips(ids).size != ids.size) return false
    val project = _state.value.project ?: return false
    val groupId = selectedClips(ids).firstNotNullOfOrNull { it.groupId } ?: "group_${UUID.randomUUID()}"
    val updated = project.clips.map { if (it.id in ids) it.copy(groupId = groupId) else it }
    if (updated == project.clips) return false
    pushUndo()
    _state.update { state -> state.copy(project = project.copy(clips = updated)) }
    persistProject(refreshLegacyClipFields = true)
    return true
}

/** Applies a constant speed choice to every selected clip as one transaction. */
fun EditorViewModel.applySpeedToSelectedClips(multiplier: Float): Boolean {
    val ids = _state.value.selectedClipIds
    val project = _state.value.project ?: return false
    if (project.clips.none { it.id in ids }) return false
    val speed = multiplier.coerceIn(0.1f, 10f)
    val updated = project.clips.map {
        if (it.id !in ids) it
        else if (it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY) {
            it.copy(speedMultiplier = speed, speedPoints = com.apexstudio.app.domain.model.SpeedCurveMath.flat(speed))
        } else it.copy(speedMultiplier = speed, speedPoints = emptyList())
    }
    if (updated == project.clips) return false
    pushUndo()
    val duration = computeTimelineDurationMs(updated, project.audioTracks)
    _state.update { it.copy(project = project.copy(clips = updated, durationMs = duration), durationMs = duration, playbackSpeed = speed) }
    persistProject(refreshLegacyClipFields = true)
    return true
}

/** Applies an actual GPU preset config to each selected clip; this is not a
 * global preview-only change. */
fun EditorViewModel.applyFilterToSelectedClips(config: GpuFilterConfig): Boolean =
    applyGpuConfigToSelectedClips(config)

fun EditorViewModel.applyLutToSelectedClips(profileId: String, intensity: Float = 1f): Boolean {
    val profile = GpuColorProfiles.findById(profileId) ?: return false
    return applyGpuConfigToSelectedClips(profile.applyWithIntensity(intensity))
}

private fun EditorViewModel.applyGpuConfigToSelectedClips(config: GpuFilterConfig): Boolean {
    val ids = _state.value.selectedClipIds
    val project = _state.value.project ?: return false
    if (project.clips.none { it.id in ids }) return false
    val updated = project.clips.map { if (it.id in ids) it.copy(gpuFilterConfig = config) else it }
    if (updated == project.clips) return false
    pushUndo()
    _state.update { state ->
        state.copy(
            project = project.copy(clips = updated),
            activeGpuFilterConfig = config,
            activeFilterId = config.filterPresetId,
            filterIntensity = config.filterIntensity
        )
    }
    persistProject(refreshLegacyClipFields = true)
    return true
}
