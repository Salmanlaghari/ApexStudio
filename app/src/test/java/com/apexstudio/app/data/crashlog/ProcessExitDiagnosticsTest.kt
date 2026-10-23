package com.apexstudio.app.data.crashlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class ProcessExitDiagnosticsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { CrashLog.clear(context) }

    @Test fun markerWithoutOsEvidenceDoesNotClaimNativeCrash() {
        ProcessExitDiagnostics.collect(context, "EditorScreen: composable start")
        val report = CrashLog.read(context).content
        assertTrue(report.contains("Exit cause is unknown"))
        assertTrue(report.contains("OS memory kill"))
        assertFalse(report.contains("Likely NATIVE"))
        assertTrue(report.contains("not a root cause"))
    }

    @Test fun markerNeverReplacesAnExistingJvmStack() {
        val stack = "Thread: main\njava.lang.IllegalStateException: actual failure"
        File(context.filesDir, CrashLog.FILENAME).writeText(stack)
        ProcessExitDiagnostics.collect(context, "older breadcrumb")
        assertEquals(stack, CrashLog.read(context).content)
    }

    @Test fun traceReaderPreservesBinaryAndRejectsOversizedTraces() {
        val bytes = byteArrayOf(0, 1, -1, 12, 90)
        assertArrayEquals(bytes, ProcessExitDiagnostics.readBoundedTrace(ByteArrayInputStream(bytes)))
        assertNull(ProcessExitDiagnostics.readBoundedTrace(ByteArrayInputStream(ByteArray(2 * 1024 * 1024 + 1))))
    }

    @Test fun sharingIncludesTheLogAndBinaryTombstoneWithoutChangingEither() {
        val log = File(context.filesDir, CrashLog.FILENAME).apply { writeText("OS native crash evidence") }
        val trace = File(context.filesDir, ProcessExitDiagnostics.TRACE_FILENAME).apply { writeBytes(byteArrayOf(0, 4, -1)) }
        val archive = CrashReportShare.createArchive(context)
        ZipFile(archive).use { zip ->
            assertEquals(log.readText(), zip.getInputStream(zip.getEntry(log.name)).bufferedReader().readText())
            assertArrayEquals(trace.readBytes(), zip.getInputStream(zip.getEntry(trace.name)).readBytes())
        }
        assertTrue(CrashLog.clear(context))
        assertFalse(log.exists())
        assertFalse(trace.exists())
        assertFalse(archive.exists())
    }

    @Test fun memoryKillAndNativeCrashHaveDifferentLabels() {
        assertEquals("low memory", ProcessExitDiagnostics.reasonLabel(3))
        assertEquals("native crash", ProcessExitDiagnostics.reasonLabel(5))
        assertEquals("ANR", ProcessExitDiagnostics.reasonLabel(6))
    }
}
