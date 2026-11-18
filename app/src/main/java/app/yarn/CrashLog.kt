package app.yarn

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * - [collectExitReasons] asks Android why Yarn last stopped, which also covers crashes the handler can't
 *   see: native code (database encryption, ML Kit, on-device AI), running out of memory, or a stuck app.
 *   Nothing leaves the phone unless the user shares it.
 */
object CrashLog {
    private const val TAG = "Yarn"
    private const val MAX_BYTES = 64 * 1024
    private var dir: File? = null

    val handler = CoroutineExceptionHandler { _, t -> record(t, fatal = false) }

    private val _pending = MutableStateFlow<String?>(null)
    /** The unseen crash report, for the inbox card. */
    val pendingReport: StateFlow<String?> = _pending

    fun install(context: Context) {
        dir = File(context.filesDir, "crash").apply { mkdirs() }
        _pending.value = pending()
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

    fun dismiss() {
        dir?.let { File(it, "last_crash.txt").delete() }
        _pending.value = null
    }

    /** Ways of stopping that mean something went wrong (not the user swiping Yarn away, or the phone reclaiming memory). */
    private val CRASHES = setOf(
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    )

    /**
     * Reads Android's record of how Yarn's earlier runs ended (Android 11+) and turns any crash since the last
     * look into a report. The first time, it looks back [firstLookBackMs], so a crash before this update shows up.
     */
    fun collectExitReasons(context: Context, now: Long = System.currentTimeMillis(), firstLookBackMs: Long = 4 * 24 * 3_600_000L) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val d = dir ?: return
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 16) }.getOrNull().orEmpty()
        val seenFile = File(d, "exits_seen.txt")
        val since = seenFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: (now - firstLookBackMs)
        exits.maxOfOrNull { it.timestamp }?.let { newest -> runCatching { seenFile.writeText(maxOf(newest, since).toString()) } }
        // A Java crash the handler already wrote up in full needs no second copy.
        val handled = pending() != null
        val recent = exits.filter { it.timestamp > since && it.reason in CRASHES }
        val nativeAt = recent.filter { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }.map { it.timestamp }
        val found = recent
            .filterNot { it.reason == ApplicationExitInfo.REASON_CRASH && handled }
            // Android files a native crash twice: once as a native crash, once as a plain crash with no detail.
            .filterNot { e -> e.reason == ApplicationExitInfo.REASON_CRASH && nativeAt.any { kotlin.math.abs(it - e.timestamp) < 3_000 } }
            .sortedByDescending { it.timestamp }
        if (found.isEmpty()) return
        val shown = found.take(MAX_SHOWN)
        val text = shown.joinToString("\n\n") { describe(it) } +
            (if (found.size > shown.size) "\n\n…and ${found.size - shown.size} earlier crash(es)" else "")
        runCatching {
            val file = File(d, "last_crash.txt")
            file.writeText((if (file.exists()) file.readText() + "\n\n" else "") + text)
        }
        _pending.value = pending()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun describe(e: ApplicationExitInfo): String {
        val reason = when (e.reason) {
            ApplicationExitInfo.REASON_CRASH -> "Crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            ApplicationExitInfo.REASON_ANR -> "Not responding"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Failed to start"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Stopped for using too much"
            else -> "Stopped (${e.reason})"
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(e.timestamp))
        val where = if (e.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "on screen" else "in the background"
        val head = "CRASH $time · $reason $where · Yarn ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
            "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "process ${e.processName} · status ${e.status} · memory ${e.pss / 1024} MB (rss ${e.rss / 1024} MB)" +
            (e.description?.takeIf { it.isNotBlank() && it != "crash" }?.let { "\n$it" } ?: "")
        return head + trace(e)
    }

    /**
     * For a native crash: the app library that failed, the frames around it, and (when Yarn can) the feature
     * switched off so it doesn't crash again. The other threads, just waiting, are left out.
     */
    internal fun nativeSummary(runs: List<String>): String {
        val at = runs.indexOfFirst { "/data/app/" in it && APP_LIB.containsMatchIn(it) }
        if (at < 0) return runs.joinToString("\n").take(3_000)
        val library = APP_LIB.find(runs[at])!!.groupValues[1]
        val feature = NativeGuard.featureFor(library)
        feature?.let(NativeGuard::disable)
        // The crashing thread's frames run until the next thread's name (a bare word, no brackets or path).
        val frames = ArrayList<String>()
        for (i in maxOf(0, at - 2) until minOf(runs.size, at + 30)) {
            val r = runs[i]
            if (BUILD_ID.matches(r) || r.startsWith("2?/data/misc/apexdata")) continue
            if (i > at && '(' !in r && '/' !in r) break
            frames += r.replace(Regex("/data/app/[^ ]*?/(base\\.apk|split_[^!]*\\.apk|lib/)"), "…/$1")
        }
        val what = feature?.let { "\nTurned off until the next update: ${it.label}" }.orEmpty()
        return "Crashed in $library$what\n" + frames.joinToString("\n").take(3_000)
    }

    private val BUILD_ID = Regex("""B [0-9a-f]{32}"?""")
    private val APP_LIB = Regex("""(lib[A-Za-z0-9_.\-]+?\.so)""")
    private const val MAX_SHOWN = 6

    /**
     * What Android kept of the moment it stopped: the thread dump for a hang, or the native backtrace (a binary
     * record on Android 12+, from which the readable library and function names are kept).
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun trace(e: ApplicationExitInfo): String {
        val bytes = runCatching { e.traceInputStream?.use { it.readNBytesCompat(256 * 1024) } }.getOrNull() ?: return ""
        val text = if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
            nativeSummary(printable(bytes))
        } else {
            String(bytes, Charsets.UTF_8).let { t ->
                // For a hang, the main thread is what matters.
                val main = t.indexOf("\"main\"")
                if (main >= 0) t.substring(main) else t
            }
        }
        return "\n" + text.take(8_000)
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8_192)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Runs of readable text in a binary record (library paths, function names, the abort message). */
    internal fun printable(bytes: ByteArray, min: Int = 6): List<String> {
        val runs = ArrayList<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) sb.append(c.toChar()) else {
                if (sb.length >= min) runs += sb.toString()
                sb.setLength(0)
            }
        }
        if (sb.length >= min) runs += sb.toString()
        return runs.distinct()
    }

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
