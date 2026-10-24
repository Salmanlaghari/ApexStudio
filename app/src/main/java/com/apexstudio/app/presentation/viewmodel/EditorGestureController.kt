package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.ai.mediapipe.GestureCommand
import com.apexstudio.app.ai.mediapipe.GestureCommandSession
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.abs

/** Internal capability bound to one explicit arming and an exact editor snapshot. */
internal data class EditorGestureSession(
    val id: String,
    val project: Project,
    val clipId: String,
)

data class GestureEditingUiState(
    val session: GestureCommandSession? = null,
    val message: String = "Gesture editing is OFF. Explicitly arm before using the camera.",
)

/** Pure source-coordinate planning; raw gesture values must never be handed to timeline mutations. */
internal object GestureEditPlanner {
    fun trim(clip: MediaClip, fraction: Float): Pair<Long, Long>? {
        if (!fraction.isFinite() || fraction == 0f || abs(fraction) > .10f || clip.durationMs <= 0L) return null
        val start = clip.trimStartMs.coerceIn(0L, clip.durationMs)
        val end = clip.trimEndMs.coerceIn(start, clip.durationMs)
        val span = end - start
        if (span <= 100L) return null
        val remove = (span.toDouble() * abs(fraction).toDouble()).toLong().coerceAtMost(span - 100L)
        if (remove <= 0L) return null
        return if (fraction > 0f) start to end - remove else start + remove to end
    }

    fun timelineZoom(current: Float, factor: Float): Float? {
        if (!current.isFinite() || !factor.isFinite() || factor !in .8f..1.25f) return null
        return (current * factor).coerceIn(TIMELINE_MIN_ZOOM, TIMELINE_MAX_ZOOM).takeIf { it != current }
    }
}

/** Must be invoked on Main by the foreground UI before asking for camera access. */
fun EditorViewModel.beginGestureEditing(): GestureCommandSession? {
    endGestureEditing()
    try {
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.GESTURE_EDITING)
    } catch (blocked: AiModelUnavailableException) {
        _gestureEditing.value = GestureEditingUiState(message = blocked.message ?: "Gesture editing is blocked.")
        return null
    }
    val state = _state.value
    val project = state.project ?: return null
    val clip = project.clips.firstOrNull { it.id == state.selectedClipId } ?: return null
    if (clip.type != ClipType.VIDEO || clip.trackIndex != 0 || clip.durationMs <= 0L) {
        _gestureEditing.value = GestureEditingUiState(message = "Select a main-track video before arming gesture editing.")
        return null
    }
    val id = UUID.randomUUID().toString()
    editorGestureSession = EditorGestureSession(id, project, clip.id)
    val session = GestureCommandSession(id, armed = true)
    _gestureEditing.value = GestureEditingUiState(session, "Armed: swipe trims the selected source edge; pinch zooms the timeline.")
    return session
}

/** Any stale selection/project/media edit, blocked safety or forged session disarms without mutation. */
fun EditorViewModel.applyGestureCommand(sessionId: String, command: GestureCommand): Boolean {
    val session = editorGestureSession ?: return false
    val state = _state.value
    if (sessionId != session.id || state.project != session.project || state.selectedClipId != session.clipId) {
        endGestureEditing("The editor changed. Explicitly arm again before using gestures.")
        return false
    }
    try {
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.GESTURE_EDITING)
    } catch (blocked: AiModelUnavailableException) {
        endGestureEditing(blocked.message ?: "Gesture editing is blocked.")
        return false
    }
    val clip = session.project.clips.firstOrNull { it.id == session.clipId } ?: return false
    when (command) {
        is GestureCommand.TrimByFraction -> {
            val bounds = GestureEditPlanner.trim(clip, command.delta) ?: return false
            trimClip(clip.id, bounds.first, bounds.second)
        }
        is GestureCommand.ZoomByFactor -> {
            val zoom = GestureEditPlanner.timelineZoom(state.timelineZoom, command.factor) ?: return false
            setTimelineZoom(zoom)
        }
    }
    // Only a mutation produced by this session updates its expected snapshot. External edits cannot
    // silently retarget a delayed native hand-inference callback to a different project/source.
    editorGestureSession = _state.value.project?.let { session.copy(project = it) }
    _gestureEditing.update { it.copy(message = "Gesture applied. Swipe trims are undoable; timeline zoom does not change exported pixels.") }
    return true
}

fun EditorViewModel.endGestureEditing(message: String = "Gesture editing is OFF. Camera access is stopped when the panel closes.") {
    editorGestureSession = null
    _gestureEditing.value = GestureEditingUiState(message = message)
}
