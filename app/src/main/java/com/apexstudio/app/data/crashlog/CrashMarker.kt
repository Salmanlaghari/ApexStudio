package com.apexstudio.app.data.crashlog

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the "current operation" to a file before performing a risky native
 * call (GL/EGL, AudioRecord, Equalizer, ExoPlayer/MediaCodec, ...).
 *
 * Native signals (SIGSEGV/SIGABRT) bypass the JVM UncaughtExceptionHandler, so
 * they cannot be caught in Kotlin. But if the process dies mid-operation, the
 * marker file remains. On the next launch [readAndConsume] returns the step the
 * previous run died in, which pinpoints the exact native crash location.
 */
object CrashMarker {
    private const val FILENAME = "crash_marker.txt"

    fun mark(context: Context, step: String) {
        try {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            File(context.filesDir, FILENAME).writeText("[$ts] $step")
        } catch (_: Throwable) {
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILENAME).delete()
        } catch (_: Throwable) {
        }
    }

    /** Returns the last recorded step (if any) and deletes the marker file. */
    fun readAndConsume(context: Context): String? {
        return try {
            val f = File(context.filesDir, FILENAME)
            if (f.exists()) {
                val text = f.readText()
                f.delete()
                text.ifBlank { null }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
