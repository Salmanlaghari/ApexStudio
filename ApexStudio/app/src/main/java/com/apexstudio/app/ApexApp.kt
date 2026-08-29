package com.apexstudio.app

import android.app.Application
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

class ApexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            Log.e("ApexCrash", "Uncaught exception on ${thread.name}", throwable)
            try {
                openFileOutput("last_crash.txt", MODE_PRIVATE).use { out ->
                    out.write("Thread: ${thread.name}\n")
                    out.write(sw.toString())
                }
            } catch (_: Throwable) { }
            default?.uncaughtException(thread, throwable)
        }
    }
}
