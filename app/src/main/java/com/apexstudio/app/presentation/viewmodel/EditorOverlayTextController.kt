package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
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


fun EditorViewModel.openStickerPanel() = _state.update { it.copy(stickerPanelOpen = true) }

fun EditorViewModel.closeStickerPanel() = _state.update { it.copy(stickerPanelOpen = false) }

fun EditorViewModel.addStickerOverlay(symbolOrUri: String, category: String, name: String) {
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
}


fun EditorViewModel.openTextPanel() = _state.update { it.copy(textPanelOpen = true) }

fun EditorViewModel.closeTextPanel() = _state.update { it.copy(textPanelOpen = false) }

fun EditorViewModel.selectTextOverlay(id: String?) = _state.update { it.copy(selectedTextOverlayId = id) }


fun EditorViewModel.addTextOverlay(clipId: String, text: String = "Text", x: Float = 0.5f, y: Float = 0.35f) {
    val overlay = com.apexstudio.app.domain.model.TextOverlay.of(
        text = text, x = x, y = y
    )
    updateClip(clipId) { it.copy(textOverlays = it.textOverlays + overlay) }
    _state.update { it.copy(selectedTextOverlayId = overlay.id) }
}


fun EditorViewModel.setTextOverlayAnimation(clipId: String, overlayId: String, animationType: String, durationMs: Long = 800L) =
    updateTextOverlay(clipId, overlayId) { it.copy(animationType = animationType, animationDurationMs = durationMs) }


fun EditorViewModel.updateTextOverlay(clipId: String, overlayId: String, transform: (com.apexstudio.app.domain.model.TextOverlay) -> com.apexstudio.app.domain.model.TextOverlay) {
    updateClip(clipId) { clip ->
        clip.copy(
            textOverlays = clip.textOverlays.map {
                if (it.id == overlayId) transform(it) else it
            }
        )
    }
}


fun EditorViewModel.moveTextOverlay(
    clipId: String,
    overlayId: String,
    dx: Float,
    dy: Float,
    persist: Boolean = true
) {
    updateClip(clipId, persist = persist) { clip ->
        clip.copy(
            textOverlays = clip.textOverlays.map {
                if (it.id == overlayId) {
                    it.copy(
                        x = (it.x + dx).coerceIn(0.04f, 0.96f),
                        y = (it.y + dy).coerceIn(0.06f, 0.94f)
                    )
                } else it
            }
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


fun EditorViewModel.duplicateTextOverlay(clipId: String, overlayId: String) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val target = clip.textOverlays.firstOrNull { it.id == overlayId } ?: return
    val newId = "txt_${System.currentTimeMillis()}"
    val duplicated = target.copy(
        id = newId,
        x = (target.x + 0.05f).coerceIn(0.1f, 0.9f),
        y = (target.y + 0.05f).coerceIn(0.1f, 0.9f)
    )
    updateClip(clipId) { it.copy(textOverlays = it.textOverlays + duplicated) }
    _state.update { it.copy(selectedTextOverlayId = newId) }
}


fun EditorViewModel.applyTextPreset(clipId: String, overlayId: String, preset: com.apexstudio.app.data.text.TextPreset) =
    updateTextOverlay(clipId, overlayId) { preset.applyTo(it) }


fun EditorViewModel.removeTextOverlay(clipId: String, overlayId: String) {
    updateClip(clipId) { clip ->
        clip.copy(textOverlays = clip.textOverlays.filterNot { it.id == overlayId })
    }
    if (_state.value.selectedTextOverlayId == overlayId) {
        _state.update { it.copy(selectedTextOverlayId = null) }
    }
}


fun EditorViewModel.selectSticker(id: String?) {
    _state.update { it.copy(selectedStickerId = id) }
}


fun EditorViewModel.updateTextOverlayTiming(clipId: String, textId: String, startMs: Long, endMs: Long) {
    updateClip(clipId) { clip ->
        val updated = clip.textOverlays.map {
            if (it.id == textId) it.copy(startMs = startMs, endMs = endMs) else it
        }
        clip.copy(textOverlays = updated)
    }
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
