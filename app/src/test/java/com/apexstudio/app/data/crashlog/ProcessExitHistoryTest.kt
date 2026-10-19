package com.apexstudio.app.data.crashlog

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProcessExitHistoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() {
        CrashLog.clear(context)
        context.getSharedPreferences("process_exit_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun record(reason: Int, trace: ByteArray? = null, process: String = context.packageName) {
        val evidence = ApplicationExitInfoBuilder.newBuilder()
            .setProcessName(process)
            .setTimestamp(System.currentTimeMillis() - 1_000)
            .setReason(reason)
            .setStatus(if (reason == ApplicationExitInfo.REASON_CRASH_NATIVE) 11 else 0)
            .setDescription("OS evidence for regression test")
        if (trace != null) evidence.setTraceInputStream(ByteArrayInputStream(trace))
        shadowOf(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .addApplicationExitInfo(evidence.build())
    }

    @Test fun nativeExitCapturesBinaryTombstoneAndDoesNotRecollectIt() {
        val trace = byteArrayOf(0, 4, 12, -1)
        record(ApplicationExitInfo.REASON_CRASH_NATIVE, trace)
        ProcessExitDiagnostics.collect(context, "EditorScreen: start")
        val report = CrashLog.read(context).content
        assertTrue(report.contains("Reason: native crash (5), status/signal=11"))
        assertTrue(report.contains("not a root cause"))
        assertArrayEquals(trace, File(context.filesDir, ProcessExitDiagnostics.TRACE_FILENAME).readBytes())
        ProcessExitDiagnostics.collect(context, "newer marker")
        assertEquals(report, CrashLog.read(context).content)
    }

    @Test fun memoryKillIsNotCalledNativeAndDiscardsStaleTombstone() {
        File(context.filesDir, ProcessExitDiagnostics.TRACE_FILENAME).writeText("stale")
        record(ApplicationExitInfo.REASON_LOW_MEMORY)
        ProcessExitDiagnostics.collect(context, "last operation")
        assertTrue(CrashLog.read(context).content.contains("Reason: low memory"))
        assertFalse(File(context.filesDir, ProcessExitDiagnostics.TRACE_FILENAME).exists())
    }

    @Test fun anotherProcessCannotSupplyOurCrashDiagnosis() {
        record(ApplicationExitInfo.REASON_CRASH_NATIVE, process = "other.process")
        ProcessExitDiagnostics.collect(context, "last operation")
        assertTrue(CrashLog.read(context).content.contains("Exit cause is unknown"))
    }
}
