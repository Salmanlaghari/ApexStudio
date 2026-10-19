package com.apexstudio.app.data.export

import com.apexstudio.app.presentation.state.ExportState
import org.junit.Assert.*
import org.junit.Test

class AudioMixExportNoticeTest {
    @Test fun completeMixDoesNotInventAWarning() {
        assertNull(AudioMixExportNotice.forSkippedTracks(0))
        assertNull(AudioMixExportNotice.forSkippedTracks(-1))
    }

    @Test fun skippedSourcesReportCountAndIncompleteAudio() {
        assertTrue(AudioMixExportNotice.forSkippedTracks(1)!!.contains("1 added audio track was skipped"))
        assertTrue(AudioMixExportNotice.forSkippedTracks(3)!!.contains("3 added audio tracks were skipped"))
        assertTrue(AudioMixExportNotice.forSkippedTracks(Int.MAX_VALUE)!!.contains(Int.MAX_VALUE.toString()))
    }

    @Test fun failedMixExplicitlyDisclosesMissingAudioWithoutExposingRawExceptions() {
        assertTrue(AudioMixExportNotice.MIX_FAILED.contains("does not include the requested added audio"))
        assertFalse(AudioMixExportNotice.MIX_FAILED.contains("file://"))
    }

    @Test fun warningPreservesUsableOutputAndIsNotAFatalError() {
        val engine = ExportEngine.ExportProgressState(
            progress = 1f, outputUri = "file:///saved.mp4", warning = AudioMixExportNotice.MIX_FAILED
        )
        val ui = ExportState(
            isExporting = engine.isExporting, progress = engine.progress,
            outputUri = engine.outputUri, error = engine.error, warning = engine.warning
        )
        assertEquals("file:///saved.mp4", ui.outputUri)
        assertNull(ui.error)
        assertEquals(AudioMixExportNotice.MIX_FAILED, ui.warning)
        assertFalse(ui.isExporting)
        assertNull(ExportEngine.ExportProgressState(isExporting = true).warning)
        assertNull(ExportState().warning)
    }
}
