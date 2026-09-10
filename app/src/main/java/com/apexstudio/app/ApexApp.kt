package com.apexstudio.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.crashlog.CrashLog
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApexApp : Application() {
    val isDebug: Boolean = true

    override fun onCreate() {
        super.onCreate()

        // If the previous run died (native signal bypasses the JVM handler), the
        // CrashMarker file still holds the last operation it was performing.
        // Surface it as a crash log so the diagnostics screen can show the exact step.
        val marker = CrashMarker.readAndConsume(this)
        if (marker != null) {
            val existing = CrashLog.read(this)
            if (!existing.exists || existing.content.isBlank()) {
                try {
                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val msg = buildString {
                        appendLine("Timestamp: $ts")
                        appendLine("Thread: <process died before a stack could be captured>")
                        appendLine()
                        appendLine("Likely NATIVE crash at step: $marker")
                        appendLine("(Native signals bypass Thread.setDefaultUncaughtExceptionHandler,")
                        appendLine("so no JVM stack was captured. Reproduce and check adb logcat for")
                        appendLine("the 'Fatal signal' line / tombstone for the precise library & backtrace.)")
                    }
                    File(filesDir, CrashLog.FILENAME).writeText(msg)
                } catch (_: Throwable) {
                }
            }
        }

        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())
            Log.e("ApexCrash", "Uncaught exception on ${thread.name}", throwable)
            try {
                val file = File(filesDir, "last_crash.txt")
                FileOutputStream(file).use { out ->
                    out.write("Timestamp: $timestamp\n".toByteArray())
                    out.write("Thread: ${thread.name}\n".toByteArray())
                    out.write(sw.toString().toByteArray())
                    out.flush()
                }
            } catch (writeError: Throwable) {
                Log.e("ApexCrash", "Failed to write crash log to ${filesDir}/last_crash.txt", writeError)
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
