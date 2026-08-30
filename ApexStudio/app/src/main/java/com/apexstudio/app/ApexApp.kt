package com.apexstudio.app

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
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
