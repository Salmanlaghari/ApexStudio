package com.apexstudio.app.data.crashlog

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.InputStream

/** Local-only postmortem evidence. A stale breadcrumb is NOT proof of a native crash. */
object ProcessExitDiagnostics {
    const val TRACE_FILENAME = "last_native_tombstone.pb"
    private const val MAX_TRACE_BYTES = 2 * 1024 * 1024
    private const val PREFS = "process_exit_diagnostics"

    fun collect(context: Context, marker: String?) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCollected = preferences.getLong("last_exit_timestamp", 0)
        val previousStart = preferences.getLong("last_start_timestamp", 0)
        preferences.edit().putLong("last_start_timestamp", System.currentTimeMillis()).apply()
        val exit = if (Build.VERSION.SDK_INT >= 30) runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .filter { it.processName == context.packageName && it.timestamp > lastCollected && it.timestamp >= previousStart }
                .maxByOrNull { it.timestamp }
        }.getOrNull() else null
        val file = File(context.filesDir, CrashLog.FILENAME)
        if (exit != null && Build.VERSION.SDK_INT >= 30) {
            preferences.edit().putLong("last_exit_timestamp", exit.timestamp).apply()
            // User-requested shutdowns are not crashes; don't generate alarming reports.
            if (exit.reason in setOf(ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_EXIT_SELF)) return
            val trace = File(context.filesDir, TRACE_FILENAME)
            trace.delete() // Never attach a stale tombstone to a different exit.
            val traceStatus = if (Build.VERSION.SDK_INT >= 31 && exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                runCatching {
                    exit.traceInputStream?.use { input ->
                        val bytes = readBoundedTrace(input)
                        if (bytes != null) {
                            trace.writeBytes(bytes)
                            "Native tombstone saved locally ($TRACE_FILENAME, ${bytes.size} bytes; protobuf, not a text stack)."
                        } else "Native tombstone exceeded the local size limit; use adb logcat."
                    } ?: "The OS did not retain a tombstone; use adb logcat Fatal signal/backtrace."
                }.getOrElse { "Native tombstone could not be read; use adb logcat Fatal signal/backtrace." }
            } else "No native tombstone captured for this exit."
            val report = buildString {
                appendLine("OS process-exit evidence (timestamp=${exit.timestamp})")
                appendLine("Reason: ${reasonLabel(exit.reason)} (${exit.reason}), status/signal=${exit.status}")
                appendLine("Memory at last OS sample: PSS=${exit.pss} KiB, RSS=${exit.rss} KiB")
                appendLine("Description: ${exit.description.orEmpty().take(2048)}")
                if (marker != null) appendLine("Last breadcrumb (not a root cause): $marker")
                appendLine(traceStatus)
                appendLine("A breadcrumb alone cannot identify a native library or prove the failing operation.")
            }
            // Preserve a JVM stack written immediately before this same process exit.
            val existing = if (file.exists() && file.lastModified() >= exit.timestamp - 60_000) file.readText() else ""
            file.writeText(if (existing.isNotBlank()) "$existing\n\n$report" else report)
        } else if (marker != null && (!file.exists() || file.length() == 0L)) {
            file.writeText(unknownExitReport(marker))
        }
    }

    internal fun unknownExitReport(marker: String): String =
        "Previous process ended without a captured JVM stack.\n" +
            "Last breadcrumb (not a root cause): $marker\n" +
            "Exit cause is unknown: native crash, OS memory kill, and process termination are not distinguishable from this marker.\n" +
            "For a native diagnosis, capture adb logcat Fatal signal/backtrace or the OS tombstone.\n"

    internal fun readBoundedTrace(input: InputStream): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > MAX_TRACE_BYTES) return null
            output.write(buffer, 0, count)
        }
    }

    internal fun reasonLabel(reason: Int): String = when (reason) {
        2 -> "signaled"
        3 -> "low memory"
        4 -> "JVM crash"
        5 -> "native crash"
        6 -> "ANR"
        9 -> "excessive resource usage"
        13 -> "other OS reason"
        else -> "OS exit"
    }
}
