package com.apexstudio.app

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())
            Log.e("ApexCrash", "Uncaught exception on ${thread.name}", throwable)
            try {
                this.openFileOutput("last_crash.txt", Context.MODE_PRIVATE).use { out ->
                    out.write("Timestamp: $timestamp\n".toByteArray())
                    out.write("Thread: ${thread.name}\n".toByteArray())
                    out.write(sw.toString().toByteArray())
                }
            } catch (_: Throwable) { }
            default?.uncaughtException(thread, throwable)
        }
    }
}
