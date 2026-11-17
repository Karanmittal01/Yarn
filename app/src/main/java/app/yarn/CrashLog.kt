package app.yarn

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps Yarn standing and leaves a trail when something goes wrong.
 *
 * - [handler] goes on every background job, so one failed task (a database hiccup, a provider
 *   refusing a write) is recorded instead of closing the whole app.
 * - [install] records real crashes on the phone, so the next launch can offer to share the report.
 *   Nothing leaves the phone unless the user shares it.
 */
object CrashLog {
    private const val TAG = "Yarn"
    private const val MAX_BYTES = 64 * 1024
    private var dir: File? = null

    val handler = CoroutineExceptionHandler { _, t -> record(t, fatal = false) }

    fun install(context: Context) {
        dir = File(context.filesDir, "crash").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            runCatching { record(t, fatal = true) }
            previous?.uncaughtException(thread, t)
        }
    }

    /** Writes [t] down; a crash replaces the last crash report, other errors go to a capped log. */
    fun record(t: Throwable, fatal: Boolean) {
        Log.e(TAG, if (fatal) "Crash" else "Background error", t)
        val d = dir ?: return
        val text = report(t, fatal)
        runCatching {
            if (fatal) {
                File(d, "last_crash.txt").writeText(text)
            } else {
                val log = File(d, "errors.txt")
                if (log.length() > MAX_BYTES) log.writeText(log.readText().takeLast(MAX_BYTES / 2))
                log.appendText(text + "\n")
            }
        }
    }

    /** The crash from the last session, if the user hasn't seen it yet. */
    fun pending(): String? = dir?.let { File(it, "last_crash.txt") }?.takeIf { it.exists() }?.readText()

    fun dismiss() { dir?.let { File(it, "last_crash.txt").delete() } }

    /** Everything recorded, for "Send feedback". */
    fun all(): String = buildString {
        pending()?.let { append(it).append("\n\n") }
        dir?.let { File(it, "errors.txt") }?.takeIf { it.exists() }?.readText()?.takeLast(16_000)?.let { append(it) }
    }

    private fun report(t: Throwable, fatal: Boolean): String {
        val trace = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString().take(12_000)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return "${if (fatal) "CRASH" else "ERROR"} $time · Yarn ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
            "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n$trace"
    }
}

/** A ViewModel's scope that records failures instead of crashing, for flows shared with stateIn. */
val androidx.lifecycle.ViewModel.safeScope: kotlinx.coroutines.CoroutineScope
    get() = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + CrashLog.handler)
