package com.apexstudio.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.crashlog.CrashLog
import com.apexstudio.app.data.filter.FilterManifest
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApexApp : Application() {
    val isDebug: Boolean = true

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        com.google.android.play.core.splitcompat.SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Scan bundled LUTs so FilterManifest.presetById() resolves template filters.
        // initialize() handles its own exceptions internally.
        FilterManifest.initialize(this)

        // A leftover breadcrumb is useful context, but cannot prove a native crash.
        val marker = CrashMarker.readAndConsume(this)

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

        // OS history/trace reads must not delay the first editor frame.
        Thread({
            runCatching { com.apexstudio.app.data.crashlog.ProcessExitDiagnostics.collect(this, marker) }
                .onFailure { Log.w("ApexCrash", "Previous-exit evidence unavailable", it) }
        }, "previous-exit-diagnostics").apply { isDaemon = true }.start()
    }
}
