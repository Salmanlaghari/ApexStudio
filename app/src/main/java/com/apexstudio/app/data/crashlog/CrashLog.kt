package com.apexstudio.app.data.crashlog

import android.content.Context
import java.io.File

data class CrashLog(
    val exists: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val content: String
) {
    companion object {
        const val FILENAME = "last_crash.txt"

        fun read(context: Context): CrashLog {
            val f = File(context.filesDir, FILENAME)
            return if (f.exists()) {
                CrashLog(
                    exists = true,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified(),
                    content = runCatching { f.readText() }.getOrElse { "" }
                )
            } else {
                CrashLog(false, 0L, 0L, "")
            }
        }

        fun clear(context: Context): Boolean {
            val f = File(context.filesDir, FILENAME)
            val trace = File(context.filesDir, ProcessExitDiagnostics.TRACE_FILENAME)
            val logCleared = !f.exists() || f.delete()
            val traceCleared = !trace.exists() || trace.delete()
            File(context.cacheDir, "diagnostics/apexstudio-crash-evidence.zip").delete()
            return logCleared && traceCleared
        }
    }
}
