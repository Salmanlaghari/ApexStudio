package com.apexstudio.app.presentation.viewmodel

import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorExportCancellationTest {
    @Test fun `cancel invalidates photo ownership before cancelling preparation and keeps settings`() {
        val vm = EditorViewModel()
        val settings = vm.export.value.settings.copy(resolution = "720p")
        vm._export.value = vm.export.value.copy(isExporting = true, progress = 0.2f, settings = settings)
        val job = Job()
        vm.photoExportRun = Any()
        vm.photoExportJob = job
        var invalidatedBeforeCancellation = false
        job.invokeOnCompletion { invalidatedBeforeCancellation = vm.photoExportRun == null }
        vm.cancelCurrentExport()
        assertTrue(job.isCancelled)
        assertTrue(invalidatedBeforeCancellation)
        assertNull(vm.photoExportRun)
        assertNull(vm.photoExportJob)
        assertFalse(vm.export.value.isExporting)
        assertEquals(0f, vm.export.value.progress, 0f)
        assertEquals(settings, vm.export.value.settings)
    }

    @Test fun `repeated cancel does not erase project media`() {
        val vm = EditorViewModel()
        val project = vm.state.value.project
        vm.cancelCurrentExport()
        vm.cancelCurrentExport()
        assertEquals(project, vm.state.value.project)
        assertFalse(vm.export.value.isExporting)
    }
}
