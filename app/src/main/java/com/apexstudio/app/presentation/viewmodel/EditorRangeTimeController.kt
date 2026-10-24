package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.engine.RangeTimeOperation
import com.apexstudio.app.data.engine.RangeTimeRenderResult
import com.apexstudio.app.domain.model.*
import kotlinx.coroutines.flow.update

sealed interface RangeTimeApplyResult {
    data class Applied(val plan: RangeTimeEditPlan) : RangeTimeApplyResult
    data class Rejected(val eligibility: RangeTimeEligibility) : RangeTimeApplyResult
}

/** Returns a UI-ready reason and refuses to advertise stale or unsafe targets. */
fun EditorViewModel.rangeTimeEligibility(
    projectId: String,
    expectedClip: MediaClip,
    range: SourceRangeMs,
    operation: RangeTimeOperation
): RangeTimeEligibility {
    if (state.value.selectedClipId != expectedClip.id) {
        return RangeTimeEligibility.reject(RangeTimeRejection.CLIP_MISMATCH, "Select the original video again before applying this edit.")
    }
    val project = state.value.project
        ?: return RangeTimeEligibility.reject(RangeTimeRejection.PROJECT_MISMATCH, "No project is open.")
    if (project.id != projectId) {
        return RangeTimeEligibility.reject(RangeTimeRejection.PROJECT_MISMATCH, "The open project changed while this edit was prepared.")
    }
    val current = project.clips.firstOrNull { it.id == expectedClip.id }
        ?: return RangeTimeEligibility.reject(RangeTimeRejection.CLIP_MISMATCH, "The selected clip is no longer in this project.")
    if (current != expectedClip) {
        return RangeTimeEligibility.reject(RangeTimeRejection.CLIP_MISMATCH, "The selected clip changed while this edit was prepared. Re-select the range and try again.")
    }
    return RangeTimeEdit.eligibility(project, expectedClip.id, range, operation)
}

/**
 * Applies a local rendered whole-trimmed-clip result only after explicit Apply. [range] is relative
 * to [expectedClip.trimStartMs], even though the renderer itself consumes absolute source times.
 * Identity, complete source snapshot, operation duration and project complexity are revalidated.
 * One project snapshot makes the replace a single undo/redo transaction; original files are not
 * modified, deleted, or overwritten.
 */
fun EditorViewModel.applyRangeTimeRender(
    projectId: String,
    expectedClip: MediaClip,
    range: SourceRangeMs,
    operation: RangeTimeOperation,
    renderedAsset: RangeTimeRenderResult
): RangeTimeApplyResult {
    if (state.value.selectedClipId != expectedClip.id) {
        return rejected(RangeTimeRejection.CLIP_MISMATCH, "The editing selection changed before Apply.")
    }
    val currentProject = state.value.project
        ?: return rejected(RangeTimeRejection.PROJECT_MISMATCH, "No project is open.")
    if (currentProject.id != projectId) {
        return rejected(RangeTimeRejection.PROJECT_MISMATCH, "The open project changed while this edit was prepared.")
    }
    val currentClip = currentProject.clips.firstOrNull { it.id == expectedClip.id }
        ?: return rejected(RangeTimeRejection.CLIP_MISMATCH, "The selected clip is no longer in this project.")
    if (currentClip != expectedClip) {
        return rejected(RangeTimeRejection.CLIP_MISMATCH, "The selected clip changed while this edit was prepared.")
    }

    val planned = RangeTimeEdit.plan(currentProject, expectedClip.id, range, operation, renderedAsset)
    if (planned is RangeTimePlanResult.Rejected) return RangeTimeApplyResult.Rejected(planned.eligibility)
    val plan = (planned as RangeTimePlanResult.Planned).plan

    // Validate again immediately before taking the snapshot. Normal editor actions are serialized
    // on the UI thread; this additional check also protects callers that render off-thread.
    val latest = _state.value.project
    if (latest != currentProject || state.value.selectedClipId != expectedClip.id) {
        return rejected(RangeTimeRejection.CLIP_MISMATCH, "The project or clip changed before the edit could be applied.")
    }

    pushUndo()
    val duration = plan.updatedProject.durationMs.coerceAtLeast(1L)
    val position = livePlayheadMs().coerceIn(0L, duration)
    _playerPosition.value = position
    _state.update { state ->
        state.copy(
            project = plan.updatedProject,
            durationMs = duration,
            playerPositionMs = position,
            currentTimeMs = position,
            selectedClipId = plan.processedClipId,
            selectedOverlayClipId = null,
            canUndo = true,
            canRedo = false
        )
    }
    persistProject(refreshLegacyClipFields = true)
    return RangeTimeApplyResult.Applied(plan)
}

private fun rejected(code: RangeTimeRejection, reason: String) =
    RangeTimeApplyResult.Rejected(RangeTimeEligibility.reject(code, reason))
