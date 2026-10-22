package io.github.lightsongbook.core

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Writes uncaught exceptions to a file so the next start can show what went wrong.
 * There is no logcat on the phone without a cable, so this is the only way to see a crash.
 */
object CrashLog {
    private var file: File? = null

    @Synchronized
    fun install(filesDir: File) {
        if (file != null) return
        val target = File(filesDir, "last_crash.txt")
        file = target
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                target.writeText(describe(error, "Thread: ${thread.name}"))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): String? = file?.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    fun clear() {
        file?.delete()
    }

    fun describe(error: Throwable, header: String = ""): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        // keep our own frames and the first lines; the full trace is too long to photograph
        val lines = sw.toString().lines()
        val important = lines.filterIndexed { i, l ->
            i < 12 || l.contains("lightsongbook") || l.startsWith("Caused by")
        }.take(60)
        return (listOf(header).filter { it.isNotEmpty() } + important).joinToString("\n")
    }
}
