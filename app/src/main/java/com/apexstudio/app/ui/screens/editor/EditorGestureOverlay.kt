package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.components.GestureCameraSheet

/** UI wiring is present, but it never requests camera access while global safety is unavailable. */
@Composable
fun EditorGestureOverlay(vm: EditorViewModel, onClose: () -> Unit) {
    val gesture by vm.gestureEditing.collectAsStateWithLifecycle()
    val editor by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(editor.project, editor.selectedClipId) {
        val session = vm.editorGestureSession
        if (session != null && (session.project != editor.project || session.clipId != editor.selectedClipId)) {
            vm.endGestureEditing("The editor changed. Explicitly arm gesture control again.")
        }
    }
    DisposableEffect(vm) {
        onDispose { vm.endGestureEditing() }
    }
    GestureCameraSheet(
        session = gesture.session,
        commandsAllowed = GlobalModerationEnforcement.readiness.approved,
        disabledReason = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.GESTURE_EDITING),
        onArmRequested = { vm.beginGestureEditing() },
        onDisarmRequested = { vm.endGestureEditing() },
        onDismissRequest = { vm.endGestureEditing(); onClose() },
        onCommand = { sessionId, command -> vm.applyGestureCommand(sessionId, command) },
    )
}
