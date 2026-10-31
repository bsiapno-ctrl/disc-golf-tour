package com.discgolf.game

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Records any crash to a file so the next launch can show it on screen (no adb needed). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(filesDir, CRASH_FILE).writeText(
                    "Thread: ${t.name}\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n\n$sw"
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
