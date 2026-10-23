package com.apexstudio.app.presentation.viewmodel

import kotlinx.coroutines.flow.update

/** UI-thread action. Invalidate photo ownership before cancelling its coroutine. */
fun EditorViewModel.cancelCurrentExport() {
    photoExportRun = null
    photoExportJob?.cancel()
    photoExportJob = null
    exportEngine?.cancelExport()
    _export.update { it.copy(isExporting = false, progress = 0f, error = null, outputUri = null, warning = null) }
}
