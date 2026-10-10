package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import com.apexstudio.app.data.autoclip.AutoClipOp
import com.apexstudio.app.data.autoclip.AutoClipPlan
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.SpeedCurve
import com.apexstudio.app.ui.screens.editor.SplitLayout
import kotlinx.coroutines.flow.update

/**
 * Phase 3: executes [AutoClipPlan] ops and Edit Pack preset applications
 * using existing ViewModel primitives. No new engine paths.
 */

/** Applies an Auto Clip plan to the current project. */
fun EditorViewModel.applyAutoClipPlan(plan: AutoClipPlan) {
    val clips = _state.value.project?.clips ?: return
    val clipIds = clips.map { it.id }.toSet()
    plan.ops.forEach { op ->
        try {
            when (op) {
                is AutoClipOp.SetSpeed -> {
                    if (op.clipId in clipIds) {
                        if (op.curve == SpeedCurve.RAMP) {
                            // RAMP curve: use start=end=speed for a flat ramp.
                            setClipSpeedRamp(op.clipId, op.speed, op.speed)
                        } else {
                            setClipSpeed(op.clipId, op.speed)
                            setClipSpeedCurve(op.clipId, op.curve)
                        }
                    }
                }
                is AutoClipOp.SetTransition -> {
                    val idx = clips.indexOfFirst { it.id == op.afterClipId }
                    val next = clips.getOrNull(idx + 1)
                    if (idx >= 0 && next != null) {
                        applyTransition(op.afterClipId, next.id, op.transitionId, op.durationMs)
                    }
                }
                is AutoClipOp.SetFilter -> {
                    selectFilter(op.filterId, op.intensity)
                }
                is AutoClipOp.SetEffect -> {
                    setActiveFx(op.effectId)
                    _state.update { it.copy(fxIntensity = op.intensity.toDouble()) }
                }
                is AutoClipOp.TrimTo -> {
                    val clip = clips.firstOrNull { it.id == op.clipId } ?: return@forEach
                    val dur = clip.durationMs
                    if (dur > op.maxDurationMs) {
                        trimClip(op.clipId, clip.trimStartMs, clip.trimStartMs + op.maxDurationMs)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AutoClip", "Op failed: $op", e)
        }
    }
    persistProject()
    Log.i("AutoClip", "Applied: ${plan.summary}")
}

/** Applies an Edit Pack speed_ramp preset to a clip. */
fun EditorViewModel.applyEditPackSpeed(
    clipId: String,
    startSpeed: Float,
    endSpeed: Float,
    fixedSpeed: Float?
) {
    if (fixedSpeed != null) {
        setClipSpeed(clipId, fixedSpeed)
    } else {
        setClipSpeedRamp(clipId, startSpeed, endSpeed)
    }
    persistProject()
}

/** Applies an Edit Pack freeze preset (dramatic slow hold — honest, no true freeze in engine). */
fun EditorViewModel.applyEditPackFreeze(clipId: String) {
    // Slowest supported speed as a "hold" approximation.
    setClipSpeed(clipId, 0.1f)
    persistProject()
}

/** Applies an Edit Pack split-screen preset via PIP positioning. */
fun EditorViewModel.applyEditPackSplit(clipId: String, layout: SplitLayout) {
    updateClip(clipId) {
        it.copy(
            pipX = layout.pipX.coerceIn(0f, 1f),
            pipY = layout.pipY.coerceIn(0f, 1f),
            pipScale = layout.pipScale.coerceIn(0.1f, 1f)
        )
    }
    persistProject()
}

/** Applies an Edit Pack zoom preset via scale keyframes. */
fun EditorViewModel.applyEditPackZoom(clipId: String, zoomIn: Boolean) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val dur = clip.durationMs.coerceAtLeast(1000L)
    clearKeyframes(clipId)
    if (zoomIn) {
        addKeyframe(clipId, 0L, AnimatedTransform.Identity)
        addKeyframe(
            clipId, dur,
            AnimatedTransform(0f, 0f, 1.6f, 0f, 1f, 1f, 1f, 1f)
        )
    } else {
        addKeyframe(clipId, 0L, AnimatedTransform(0f, 0f, 1.6f, 0f, 1f, 1f, 1f, 1f))
        addKeyframe(clipId, dur, AnimatedTransform.Identity)
    }
    persistProject()
}

/** Applies an Adjust Pack / recovery preset. */
fun EditorViewModel.applyAdjustPackPreset(
    adjustments: com.apexstudio.app.domain.model.VideoAdjustments
) {
    _state.update { it.copy(adjustments = adjustments) }
    persistProject()
}
