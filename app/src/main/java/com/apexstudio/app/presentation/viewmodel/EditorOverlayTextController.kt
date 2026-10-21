package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import com.apexstudio.app.data.export.ProjectTextOverlayTimeline
import kotlinx.coroutines.flow.update


// Phase D: PiP overlay preview. setOverlayTransform persists the
// (x, y, scale, opacity) tuple so a pinch-zoom doesn't get clobbered
// by unrelated state recompositions. setOverlayClip registers /
// clears which clip owns the transform. clearOverlay resets both.
fun EditorViewModel.setOverlayTransform(transform: com.apexstudio.app.presentation.state.OverlayTransform) =
    _state.update { it.copy(overlayTransform = transform) }

fun EditorViewModel.setOverlayClip(clipId: String?) =
    _state.update { it.copy(overlayClipId = clipId) }

fun EditorViewModel.clearOverlay() = _state.update {
    it.copy(overlayClipId = null, overlayTransform = com.apexstudio.app.presentation.state.OverlayTransform.Identity)
}

fun EditorViewModel.setPendingAddAsOverlay(v: Boolean) = _state.update {
    it.copy(pendingAddAsOverlay = v)
}

fun EditorViewModel.setPendingAddAsAudio(v: Boolean) = _state.update {
    it.copy(pendingAddAsAudio = v)
}

/** Route the next media-picker result to overlay layer [layer] (1..9). */
fun EditorViewModel.setPendingAddToLayer(layer: Int?) = _state.update {
    it.copy(pendingAddToLayer = layer?.coerceIn(1, MAX_VIDEO_LAYERS - 1))
}


fun EditorViewModel.openStickerPanel() = _state.update { it.copy(stickerPanelOpen = true) }

fun EditorViewModel.closeStickerPanel() = _state.update { it.copy(stickerPanelOpen = false) }

fun EditorViewModel.addStickerOverlay(symbolOrUri: String, category: String, name: String) {
    if (_state.value.project == null || symbolOrUri.isBlank()) return
    pushUndo()
    val newSticker = StickerOverlay(
        symbolOrUri = symbolOrUri,
        category = category,
        name = name,
        x = 0.5f,
        y = 0.5f,
        sizeScale = 1f,
        opacity = 1f
    )
    _state.update { st ->
        val proj = st.project ?: return@update st
        val updatedStickers = proj.stickers + newSticker
        st.copy(project = proj.copy(stickers = updatedStickers))
    }
    persistProject()
}


fun EditorViewModel.openTextPanel() = _state.update { it.copy(textPanelOpen = true, textPanelStartsOnCaptions = false) }

/** Opens the real speech-recognition controls without running inference automatically. */
fun EditorViewModel.openCaptionsPanel() = _state.update { it.copy(textPanelOpen = true, textPanelStartsOnCaptions = true) }

fun EditorViewModel.closeTextPanel() = _state.update { it.copy(textPanelOpen = false, textPanelStartsOnCaptions = false) }

fun EditorViewModel.selectTextOverlay(id: String?) = _state.update {
    // Single selection: picking a text overlay clears a stale sticker
    // selection (TEXT outranks STICKER in resolveToolbarSelection, so a
    // leftover sticker id would resurrect the STICKER bar on deselect).
    // The clip selection is kept so the owner clip stays highlighted.
    it.copy(
        selectedTextOverlayId = id,
        selectedStickerId = if (id != null) null else it.selectedStickerId
    )
}


private data class TextOverlayOwner(val clip: MediaClip, val overlay: TextOverlay)

/** Resolve owner by explicit clip first; only non-null group IDs link across clips. */
private fun resolveTextOverlayOwner(clips: List<MediaClip>, clipId: String?, overlayId: String): TextOverlayOwner? {
    if (overlayId.isBlank()) return null
    val requested = clipId?.let { id -> clips.firstOrNull { it.id == id && it.textOverlays.any { ov -> ov.id == overlayId } } }
    requested?.let { owner -> return TextOverlayOwner(owner, owner.textOverlays.first { it.id == overlayId }) }
    clips.asSequence().flatMap { clip -> clip.textOverlays.asSequence().map { clip to it } }
        .firstOrNull { (_, overlay) -> overlay.id == overlayId && overlay.projectTextLayerId != null }
        ?.let { (clip, overlay) -> return TextOverlayOwner(clip, overlay) }
    clips.forEach { clip -> clip.textOverlays.firstOrNull { it.id == overlayId }?.let { return TextOverlayOwner(clip, it) } }
    return null
}

fun EditorViewModel.addTextOverlay(clipId: String, text: String = "Text", x: Float = 0.5f, y: Float = 0.35f) {
    if (_state.value.project?.clips?.none { it.id == clipId } != false) return
    pushUndo()
    val overlay = com.apexstudio.app.domain.model.TextOverlay.of(
        text = text, x = x, y = y
    )
    updateClip(clipId) { it.copy(textOverlays = it.textOverlays + overlay) }
    _state.update { it.copy(selectedTextOverlayId = overlay.id) }
}


fun EditorViewModel.setTextOverlayAnimation(clipId: String, overlayId: String, animationType: String, durationMs: Long = 800L) =
    updateTextOverlay(clipId, overlayId) { it.copy(animationType = animationType, animationDurationMs = durationMs) }


fun EditorViewModel.updateTextOverlay(
    clipId: String,
    overlayId: String,
    persist: Boolean = true,
    transform: (com.apexstudio.app.domain.model.TextOverlay) -> com.apexstudio.app.domain.model.TextOverlay
) {
    var found = false
    _state.update { state ->
        val project = state.project ?: return@update state
        val target = resolveTextOverlayOwner(project.clips, clipId, overlayId) ?: return@update state
        val groupId = target.overlay.projectTextLayerId
        val updatedClips = project.clips.map { clip ->
            if (groupId != null) {
                clip.copy(textOverlays = clip.textOverlays.map { overlay ->
                    if (overlay.projectTextLayerId == groupId) {
                        found = true
                        transform(overlay)
                    } else overlay
                })
            } else if (clip.id == target.clip.id) {
                clip.copy(textOverlays = clip.textOverlays.map { overlay ->
                    if (overlay.id == overlayId) { found = true; transform(overlay) } else overlay
                })
            } else clip
        }
        if (found) state.copy(project = project.copy(clips = updatedClips)) else state
    }
    if (persist && found) persistProject()
}

fun EditorViewModel.moveTextOverlay(
    clipId: String,
    overlayId: String,
    dx: Float,
    dy: Float,
    persist: Boolean = true
) {
    updateTextOverlay(clipId, overlayId, persist = persist) {
        it.copy(
            x = (it.x + dx).coerceIn(0.04f, 0.96f),
            y = (it.y + dy).coerceIn(0.06f, 0.94f)
        )
    }
}


fun EditorViewModel.setTextOverlayText(clipId: String, overlayId: String, text: String) =
    updateTextOverlay(clipId, overlayId) { it.copy(text = text) }


fun EditorViewModel.setTextOverlayColor(clipId: String, overlayId: String, colorArgb: Long) =
    updateTextOverlay(clipId, overlayId) { it.copy(colorArgb = colorArgb) }


fun EditorViewModel.setTextOverlayBg(clipId: String, overlayId: String, bgArgb: Long?) =
    updateTextOverlay(clipId, overlayId) { it.copy(bgArgb = bgArgb) }


fun EditorViewModel.setTextOverlaySize(clipId: String, overlayId: String, sizeScale: Float) =
    updateTextOverlay(clipId, overlayId) { it.copy(sizeScale = sizeScale.coerceIn(0.4f, 4f)) }


fun EditorViewModel.setTextOverlayFont(clipId: String, overlayId: String, fontFamily: String) =
    updateTextOverlay(clipId, overlayId) { it.copy(fontFamily = fontFamily) }


fun EditorViewModel.setTextOverlayStyle(clipId: String, overlayId: String, isBold: Boolean, isItalic: Boolean) =
    updateTextOverlay(clipId, overlayId) { it.copy(isBold = isBold, isItalic = isItalic) }


fun EditorViewModel.setTextOverlayStroke(clipId: String, overlayId: String, strokeArgb: Long?) =
    updateTextOverlay(clipId, overlayId) { it.copy(strokeColorArgb = strokeArgb) }


fun EditorViewModel.setTextOverlayShadow(clipId: String, overlayId: String, shadowArgb: Long?) =
    updateTextOverlay(clipId, overlayId) { it.copy(shadowColorArgb = shadowArgb) }


fun EditorViewModel.setTextOverlayAnimDuration(clipId: String, overlayId: String, durationMs: Long) =
    updateTextOverlay(clipId, overlayId) { it.copy(animationDurationMs = durationMs) }


fun EditorViewModel.setTextOverlayOutro(clipId: String, overlayId: String, outroType: String) =
    updateTextOverlay(clipId, overlayId) { it.copy(outroAnimationType = outroType) }


fun EditorViewModel.setTextOverlayOutroDuration(clipId: String, overlayId: String, durationMs: Long) =
    updateTextOverlay(clipId, overlayId) { it.copy(outroAnimationDurationMs = durationMs) }


fun EditorViewModel.setTextOverlayAlign(clipId: String, overlayId: String, align: String) =
    updateTextOverlay(clipId, overlayId) { it.copy(textAlign = align) }


fun EditorViewModel.setTextOverlayLetterSpacing(clipId: String, overlayId: String, spacingEm: Float) =
    updateTextOverlay(clipId, overlayId) { it.copy(letterSpacingEm = spacingEm.coerceIn(-0.1f, 0.5f)) }


fun EditorViewModel.setTextOverlayGradient(clipId: String, overlayId: String, gradient: Pair<Long, Long>?) =
    updateTextOverlay(clipId, overlayId) {
        it.copy(gradientStartArgb = gradient?.first, gradientEndArgb = gradient?.second)
    }


fun EditorViewModel.duplicateTextOverlay(clipId: String, overlayId: String) {
    val project = _state.value.project ?: return
    val target = resolveTextOverlayOwner(project.clips, clipId, overlayId) ?: return
    val source = target.overlay
    val newId = "txt_${java.util.UUID.randomUUID()}"
    val newGroupId = source.projectTextLayerId?.let { java.util.UUID.randomUUID().toString() }
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        state.copy(project = current.copy(clips = current.clips.map { owner ->
            val copies = owner.textOverlays.filter { overlay ->
                if (source.projectTextLayerId != null) overlay.projectTextLayerId == source.projectTextLayerId
                else owner.id == target.clip.id && overlay.id == overlayId
            }.map { overlay ->
                overlay.copy(id = newId, projectTextLayerId = newGroupId,
                    x = (overlay.x + 0.05f).coerceIn(0.1f, 0.9f),
                    y = (overlay.y + 0.05f).coerceIn(0.1f, 0.9f))
            }
            owner.copy(textOverlays = owner.textOverlays + copies)
        }), selectedTextOverlayId = newId)
    }
    persistProject()
}

fun EditorViewModel.applyTextPreset(clipId: String, overlayId: String, preset: com.apexstudio.app.data.text.TextPreset) =
    updateTextOverlay(clipId, overlayId) { preset.applyTo(it) }


/** Apply one curated text look + supported motion as a single undoable edit. */
fun EditorViewModel.applyTextDesignMotion(
    clipId: String,
    overlayId: String,
    preset: com.apexstudio.app.data.text.TextDesignMotionPreset
) = synchronized(this) {
    val state = _state.value
    if (state.selectedClipId != clipId || (state.selectedTextOverlayId != null && state.selectedTextOverlayId != overlayId)) return@synchronized
    val clip = state.project?.clips?.firstOrNull { it.id == clipId } ?: return@synchronized
    val target = clip.textOverlays.firstOrNull { it.id == overlayId } ?: return@synchronized
    val canonical = com.apexstudio.app.data.text.TextDesignMotionPack.presets.firstOrNull { it.id == preset.id }
        ?.takeIf { it == preset } ?: return@synchronized
    if (canonical.applyTo(target) == target) return@synchronized
    pushUndo()
    updateTextOverlay(clipId, overlayId) { canonical.applyTo(it) }
}

fun EditorViewModel.removeTextOverlay(clipId: String, overlayId: String) {
    val project = _state.value.project ?: return
    val target = resolveTextOverlayOwner(project.clips, clipId, overlayId) ?: return
    val groupId = target.overlay.projectTextLayerId
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        state.copy(project = current.copy(clips = current.clips.map { owner ->
            owner.copy(textOverlays = owner.textOverlays.filterNot { overlay ->
                if (groupId != null) overlay.projectTextLayerId == groupId
                else owner.id == target.clip.id && overlay.id == overlayId
            })
        }), selectedTextOverlayId = state.selectedTextOverlayId.takeUnless { it == overlayId })
    }
    persistProject()
}

fun EditorViewModel.selectSticker(id: String?) {
    // Single selection: picking a sticker clears a stale text selection
    // (otherwise the TEXT bar, higher precedence, would keep showing
    // instead of the STICKER bar). The clip selection is kept so the
    // owner clip stays highlighted.
    _state.update {
        it.copy(
            selectedStickerId = id,
            selectedTextOverlayId = if (id != null) null else it.selectedTextOverlayId
        )
    }
}


fun EditorViewModel.updateTextOverlayTiming(clipId: String, textId: String, startMs: Long, endMs: Long) {
    val project = _state.value.project ?: return
    val entry = com.apexstudio.app.data.export.ProjectCompositionTimeline.build(project.clips)
        .entries.firstOrNull { it.clip.id == clipId } ?: return
    updateTextOverlayProjectRange(textId, entry.timelineStartMs + startMs, entry.timelineStartMs + endMs, ownerClipId = clipId)
}


/** Project-clock range shown by the text duration controls. */
data class TextOverlayProjectRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/** Resolve the selected text's real owner, independently of the selected clip. */
fun EditorViewModel.textOverlayOwnerClipId(overlayId: String): String? {
    val project = _state.value.project ?: return null
    val target = resolveTextOverlayOwner(project.clips, _state.value.selectedClipId, overlayId) ?: return null
    return target.clip.id
}

/** Read the selected overlay's actual absolute project range for UI controls. */
fun EditorViewModel.textOverlayProjectRange(overlayId: String): TextOverlayProjectRange? {
    val project = _state.value.project ?: return null
    return ProjectTextOverlayTimeline.range(project.clips, overlayId, _state.value.selectedClipId)?.let {
        TextOverlayProjectRange(it.startMs, it.endMs)
    }
}

/**
 * Set an overlay's absolute V1 project range. Existing per-clip TextOverlay
 * records are segmented with the canonical composition timeline, keeping every
 * persisted field and the existing preview/export render paths intact.
 */
fun EditorViewModel.updateTextOverlayProjectRange(
    overlayId: String,
    startMs: Long,
    endMs: Long,
    ownerClipId: String? = _state.value.selectedClipId
) {
    val state = _state.value
    val project = state.project ?: return
    val timeline = com.apexstudio.app.data.export.ProjectCompositionTimeline.build(project.clips)
    if (timeline.durationMs <= 0L) return
    val target = resolveTextOverlayOwner(project.clips, ownerClipId, overlayId) ?: return
    val original = target.overlay
    if (timeline.entries.none { it.clip.id == target.clip.id }) return

    // Clamp to the real timeline. Keep the interval non-empty without ever
    // fabricating time beyond the existing composition duration.
    val start = startMs.coerceAtLeast(0L).coerceAtMost(timeline.durationMs - 1L)
    val end = endMs.coerceIn(start + 1L, timeline.durationMs)
    val groupId = original.projectTextLayerId ?: java.util.UUID.randomUUID().toString()
    val segments = ProjectTextOverlayTimeline.segments(project.clips, original.copy(projectTextLayerId = groupId), start, end)
    if (segments.isEmpty()) return
    val segmentsByClip = segments.groupBy { it.clipId }.mapValues { (_, values) -> values.map { it.overlay } }
    val updatedClips = project.clips.map { clip ->
        val withoutOverlay = clip.textOverlays.filterNot { overlay ->
            if (original.projectTextLayerId != null) overlay.projectTextLayerId == original.projectTextLayerId
            else clip.id == target.clip.id && overlay.id == overlayId
        }
        clip.copy(textOverlays = withoutOverlay + segmentsByClip[clip.id].orEmpty())
    }
    if (updatedClips == project.clips) return

    pushUndo()
    _state.update { current ->
        val currentProject = current.project ?: return@update current
        // Avoid applying a stale range over a project whose clip order changed
        // between the synchronous read and state write.
        if (currentProject != project) {
            return@update current
        }
        current.copy(project = currentProject.copy(clips = updatedClips))
    }
    persistProject()
}


fun EditorViewModel.updateStickerTiming(stickerId: String, startMs: Long, endMs: Long) {
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.stickers.map {
            if (it.id == stickerId) it.copy(startMs = startMs, endMs = endMs) else it
        }
        s.copy(project = p.copy(stickers = updated))
    }
    persistProject()
}


// ---- Sticker library + canvas editing (feature/sticker-library) ----

/**
 * Generic project-sticker updater. [persist] = false during an active
 * drag / pinch gesture (state still updates live for the preview);
 * callers persist once the gesture ends.
 */
fun EditorViewModel.updateSticker(
    stickerId: String,
    persist: Boolean = true,
    transform: (StickerOverlay) -> StickerOverlay
) {
    _state.update { s ->
        val p = s.project ?: return@update s
        // Stickers live at project level (library adds) and can also be
        // attached to individual clips; update wherever the id is found.
        s.copy(
            project = p.copy(
                stickers = p.stickers.map { if (it.id == stickerId) transform(it) else it },
                clips = p.clips.map { clip ->
                    if (clip.stickers.any { it.id == stickerId }) {
                        clip.copy(stickers = clip.stickers.map {
                            if (it.id == stickerId) transform(it) else it
                        })
                    } else clip
                }
            )
        )
    }
    if (persist) persistProject()
}

fun EditorViewModel.moveSticker(stickerId: String, dx: Float, dy: Float, persist: Boolean = true) =
    updateSticker(stickerId, persist) {
        it.copy(
            x = (it.x + dx).coerceIn(0f, 1f),
            y = (it.y + dy).coerceIn(0f, 1f)
        )
    }

fun EditorViewModel.setStickerSizeScale(stickerId: String, scale: Float, persist: Boolean = true) =
    updateSticker(stickerId, persist) { it.copy(sizeScale = scale.coerceIn(0.1f, 8f)) }

/**
 * Toggles a sticker position keyframe at the current playhead
 * (same 150ms window + pin-current-transform behaviour as
 * [toggleTextKeyframeAtPlayhead]): removes the keyframe near the
 * playhead, or adds one pinning the sticker's interpolated transform
 * at the playhead so dragging the playhead then the sticker moves
 * only that keyframe's offset.
 */
fun EditorViewModel.toggleStickerKeyframeAtPlayhead(stickerId: String) {
    val sticker = _state.value.project?.stickers?.firstOrNull { it.id == stickerId } ?: return
    val playheadMs = livePlayheadMs()
    val existingKf = sticker.keyframes.keyframes.firstOrNull {
        kotlin.math.abs(it.timeMs - playheadMs) <= 150L
    }
    if (existingKf != null) {
        updateSticker(stickerId) { st ->
            st.copy(keyframes = KeyframeTrack(st.keyframes.keyframes.filter { it.id != existingKf.id }))
        }
    } else {
        val t = sticker.effectiveTransformAt(playheadMs)
        val kf = Keyframe(
            id = java.util.UUID.randomUUID().toString(),
            timeMs = playheadMs,
            translateX = t.translateX,
            translateY = t.translateY,
            scale = t.scale,
            rotationDeg = t.rotationDeg,
            opacity = t.opacity
        )
        updateSticker(stickerId) { st ->
            val without = st.keyframes.keyframes.filter { it.timeMs != playheadMs }
            st.copy(keyframes = KeyframeTrack(without + kf).sorted())
        }
    }
}

/**
 * Moves a sticker keyframe to a new timeline position (from diamond
 * drag on the timeline sticker lane).
 */
fun EditorViewModel.moveStickerKeyframe(stickerId: String, keyframeId: String, newTimeMs: Long) {
    updateSticker(stickerId) { st ->
        st.copy(
            keyframes = KeyframeTrack(
                st.keyframes.keyframes.map {
                    if (it.id == keyframeId) it.copy(timeMs = newTimeMs.coerceAtLeast(0L)) else it
                }
            ).sorted()
        )
    }
}

// ---- Text mask + mask keyframes (Text Reveal template) ----

/**
 * Sets/replaces the mask on a text overlay. Mask keyframes are kept:
 * they ride on the new mask's shape/feather, only the centre is
 * animated. Removing the mask also drops its keyframes (a mask with
 * no shape has nothing to animate).
 */
fun EditorViewModel.updateTextMask(
    clipId: String,
    overlayId: String,
    mask: VideoMask?
) {
    updateTextOverlay(clipId, overlayId) { ov ->
        if (mask == null) ov.copy(mask = null, maskKeyframes = emptyList())
        else ov.copy(mask = mask)
    }
}

/**
 * Toggles a mask-centre keyframe at the current playhead (same 150ms
 * window as the transform strips): removes the keyframe near the
 * playhead, or adds one pinning the mask's interpolated centre at
 * the playhead — so the user can park the playhead mid-reveal,
 * drag the wipe, and pin it there. No-op when the overlay has no mask.
 */
fun EditorViewModel.toggleTextMaskKeyframeAtPlayhead(clipId: String, overlayId: String) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val overlay = clip.textOverlays.firstOrNull { it.id == overlayId } ?: return
    if (overlay.mask == null) return
    val playheadMs = livePlayheadMs()
    val existing = overlay.maskKeyframes.firstOrNull {
        kotlin.math.abs(it.timeMs - playheadMs) <= 150L
    }
    if (existing != null) {
        updateTextOverlay(clipId, overlayId) { ov ->
            ov.copy(maskKeyframes = ov.maskKeyframes.filter { it.id != existing.id })
        }
    } else {
        val centre = overlay.effectiveMaskAt(playheadMs) ?: return
        val kf = TextMaskKeyframe.at(playheadMs, centre.centerX, centre.centerY)
        updateTextOverlay(clipId, overlayId) { ov ->
            val without = ov.maskKeyframes.filter { it.timeMs != playheadMs }
            ov.copy(maskKeyframes = (without + kf).sortedBy { it.timeMs })
        }
    }
}

/**
 * Moves a mask keyframe to a new timeline position (from diamond
 * drag on the timeline text lane's gold mask strip).
 */
fun EditorViewModel.moveTextMaskKeyframe(
    clipId: String,
    overlayId: String,
    keyframeId: String,
    newTimeMs: Long
) {
    updateTextOverlay(clipId, overlayId) { ov ->
        ov.copy(
            maskKeyframes = ov.maskKeyframes.map {
                if (it.id == keyframeId) it.copy(timeMs = newTimeMs.coerceAtLeast(0L)) else it
            }.sortedBy { it.timeMs }
        )
    }
}

/**
 * One-tap "Text Reveal" template (CapCut-style): at the playhead,
 * adds an editable "Beautiful" text clip, the original walking-cat
 * sticker on the sticker track above it, and a LINEAR mask on the
 * text keyframed so the text reveals exactly as the cat passes —
 * the mask wipe follows the sticker (sticker-assisted reveal).
 *
 * Everything stays fully editable afterwards: text (word/font/size),
 * sticker (position + its keyframes), mask (shape/feather + its
 * keyframes), durations (trim the text/sticker spans on the timeline).
 *
 * Single undo step for the text part (the clip-level undo stack
 * snapshots clips only — the project-level sticker follows the
 * same rule as any manually added sticker: delete it to remove it).
 * No-op when the project has no clip to attach the text to.
 */
fun EditorViewModel.applyTextRevealTemplate() {
    val proj = _state.value.project ?: return
    val clip = proj.clips.firstOrNull { it.id == _state.value.selectedClipId }
        ?: proj.clips.firstOrNull() ?: return
    val start = livePlayheadMs()
    val duration = TEXT_REVEAL_TEMPLATE_DURATION_MS
    val end = start + duration

    // Walking cat: travels left -> right across the whole frame,
    // starting/ending just off-screen (base x 0.5 + offsets).
    val stickerKeyframes = KeyframeTrack(
        listOf(
            Keyframe(
                id = java.util.UUID.randomUUID().toString(),
                timeMs = start,
                translateX = -0.55f
            ),
            Keyframe(
                id = java.util.UUID.randomUUID().toString(),
                timeMs = end,
                translateX = 0.55f
            )
        )
    ).sorted()
    val sticker = StickerOverlay(
        id = java.util.UUID.randomUUID().toString(),
        symbolOrUri = "",
        category = "animated",
        name = "Walking Cat",
        x = 0.5f,
        y = 0.5f,
        sizeScale = 1.0f,
        startMs = start,
        endMs = end,
        keyframes = stickerKeyframes,
        assetPath = "lottie/walking_cat.json"
    )

    // LINEAR wipe: the kept side is left of the edge, so sweeping
    // centreX -0.1 -> 1.1 reveals the text left-to-right. The wipe
    // leads the cat slightly (cat at -0.05 when the edge is at -0.1;
    // cat at 1.05 when the edge is at 1.1), so the word appears
    // exactly as the cat passes it.
    val mask = VideoMask(
        shape = MaskShape.LINEAR,
        centerX = -0.1f,
        centerY = 0.5f,
        sizeX = 0.3f,
        sizeY = 1.0f,
        feather = 0.15f,
        invert = false,
        opacity = 1f,
        enabled = true
    )
    val maskKeyframes = listOf(
        TextMaskKeyframe.at(start, -0.1f, 0.5f),
        TextMaskKeyframe.at(end, 1.1f, 0.5f)
    )
    val text = TextOverlay.of(
        text = "Beautiful",
        x = 0.5f,
        y = 0.5f,
        sizeScale = 1.8f,
        colorArgb = 0xFFFFFFFFL,
        isBold = true,
        startMs = start,
        endMs = end,
        mask = mask,
        maskKeyframes = maskKeyframes
    )

    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        s.copy(
            project = p.copy(
                clips = p.clips.map {
                    if (it.id == clip.id) it.copy(textOverlays = it.textOverlays + text) else it
                },
                stickers = p.stickers + sticker
            ),
            selectedTextOverlayId = text.id
        )
    }
    persistProject()
}

/** Duration of the Text Reveal template effect (matches the ~2s Reel). */
const val TEXT_REVEAL_TEMPLATE_DURATION_MS = 2000L

fun EditorViewModel.rotateSticker(stickerId: String, deltaDeg: Float, persist: Boolean = true) =
    updateSticker(stickerId, persist) {
        it.copy(rotationDeg = (it.rotationDeg + deltaDeg) % 360f)
    }

fun EditorViewModel.removeSticker(stickerId: String) {
    val project = _state.value.project ?: return
    if (project.stickers.none { it.id == stickerId } && project.clips.none { clip -> clip.stickers.any { it.id == stickerId } }) return
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val cleared = s.copy(
            project = p.copy(
                stickers = p.stickers.filterNot { it.id == stickerId },
                clips = p.clips.map { clip ->
                    clip.copy(stickers = clip.stickers.filterNot { it.id == stickerId })
                }
            )
        )
        if (s.selectedStickerId == stickerId) cleared.copy(selectedStickerId = null) else cleared
    }
    persistProject()
}

fun EditorViewModel.setStickerCrop(
    stickerId: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
) = updateSticker(stickerId) {
    it.copy(cropLeft = left, cropTop = top, cropRight = right, cropBottom = bottom)
}

fun EditorViewModel.setStickerCutout(stickerId: String, shape: String) =
    updateSticker(stickerId) { it.copy(cutoutShape = shape) }

/** Add a bundled PNG sticker from the library to the project canvas. */
fun EditorViewModel.addStickerAsset(assetPath: String, name: String, category: String) {
    if (_state.value.project == null || assetPath.isBlank()) return
    pushUndo()
    val newSticker = StickerOverlay(
        symbolOrUri = "",
        category = category,
        name = name,
        assetPath = assetPath,
        x = 0.5f,
        y = 0.5f,
        sizeScale = 1f,
        opacity = 1f
    )
    _state.update { st ->
        val proj = st.project ?: return@update st
        st.copy(
            project = proj.copy(stickers = proj.stickers + newSticker),
            selectedStickerId = newSticker.id,
            stickerPanelOpen = false
        )
    }
    persistProject()
}
