package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.aireport.AiContentReport
import com.apexstudio.app.data.aireport.AiContentReportReason
import com.apexstudio.app.data.aireport.AiContentReportStore
import com.apexstudio.app.data.aireport.AiContentReportTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiContentReportUiState(
    val isSaving: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false
)

/** Binds an explicit report action to the private local journal; no network client is used. */
class AiContentReportController(
    private val store: AiContentReportStore?,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow(AiContentReportUiState())
    val state: StateFlow<AiContentReportUiState> = _state.asStateFlow()

    fun submit(target: AiContentReportTarget?, reason: AiContentReportReason) {
        if (_state.value.isSaving) return
        if (target == null || target.projectId.isBlank() || target.clipId.isBlank()) {
            _state.value = AiContentReportUiState(message = "Select a valid project clip before flagging AI content.")
            return
        }
        val destination = store
        if (destination == null) {
            _state.value = AiContentReportUiState(message = "Could not save the report on this device.")
            return
        }
        _state.value = AiContentReportUiState(isSaving = true, message = "Saving report on this device…")
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    destination.append(AiContentReport(target = target, reason = reason))
                }
                _state.value = AiContentReportUiState(message = "Saved on this device. It was not sent to the developer.", saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = AiContentReportUiState(message = "Could not save the report on this device. Nothing was sent.")
            }
        }
    }

    fun clearFeedback() {
        if (!_state.value.isSaving) _state.value = AiContentReportUiState()
    }
}
