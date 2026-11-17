package app.yarn

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CrashLogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val am get() = shadowOf(context.getSystemService(ActivityManager::class.java))
    private val now = 1_800_000_000_000L

    @Before fun clean() {
        File(context.filesDir, "crash").deleteRecursively()
        CrashLog.install(context)
    }

    private fun exit(reason: Int, at: Long, description: String? = null, trace: ByteArray? = null) =
        am.addApplicationExitInfo(
            ApplicationExitInfoBuilder.newBuilder()
                .setReason(reason).setTimestamp(at).setProcessName(context.packageName).setDescription(description)
                .setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED).setPss(180_000).setRss(240_000)
                .apply { trace?.let { setTraceInputStream(it.inputStream()) } }
                .build(),
        )

    @Test fun nativeCrashBecomesAReport() {
        // A tombstone is binary; its library and function names are what's worth keeping.
        val tombstone = byteArrayOf(0x0A, 0x12) + "/data/app/lib/arm64/libsqlcipher.so".toByteArray() + byteArrayOf(0, 1, 2) +
            "sqlite3VdbeExec+1234".toByteArray() + byteArrayOf(0x7F, 0x00)
        exit(ApplicationExitInfo.REASON_CRASH_NATIVE, now - 3_600_000, "crash", tombstone)
        CrashLog.collectExitReasons(context, now)
        val report = CrashLog.pendingReport.value!!
        assertTrue(report, report.contains("Native crash in the background"))
        assertTrue(report, report.contains("libsqlcipher.so"))
        assertTrue(report, report.contains("sqlite3VdbeExec"))
        assertTrue(report, report.contains("memory 175 MB"))
    }

    @Test fun hangKeepsTheMainThread() {
        val traces = "----- pid 123 -----\n\"Binder:1\" prio=5\n  at a.b\n\"main\" prio=5 tid=1 Blocked\n  at app.yarn.Notifier.post(Notifier.kt:10)\n".toByteArray()
        exit(ApplicationExitInfo.REASON_ANR, now - 60_000, "Input dispatching timed out", traces)
        CrashLog.collectExitReasons(context, now)
        val report = CrashLog.pendingReport.value!!
        assertTrue(report, report.contains("Not responding"))
        assertTrue(report, report.contains("Input dispatching timed out"))
        assertTrue(report, report.startsWith("CRASH") && report.contains("\"main\" prio=5"))
    }

    @Test fun ordinaryExitsAndOldOnesAreIgnored() {
        exit(ApplicationExitInfo.REASON_USER_REQUESTED, now - 60_000)
        exit(ApplicationExitInfo.REASON_LOW_MEMORY, now - 60_000)
        exit(ApplicationExitInfo.REASON_CRASH_NATIVE, now - 10 * 24 * 3_600_000L)
        CrashLog.collectExitReasons(context, now)
        assertNull(CrashLog.pendingReport.value)
    }

    @Test fun eachCrashIsReportedOnce() {
        exit(ApplicationExitInfo.REASON_CRASH_NATIVE, now - 60_000, "crash")
        CrashLog.collectExitReasons(context, now)
        CrashLog.dismiss()
        CrashLog.collectExitReasons(context, now + 1_000)
        assertNull(CrashLog.pendingReport.value)
    }

    @Test fun javaCrashAlreadyWrittenUpIsNotRepeated() {
        CrashLog.record(IllegalStateException("boom"), fatal = true)
        exit(ApplicationExitInfo.REASON_CRASH, now - 60_000, "java.lang.IllegalStateException: boom")
        CrashLog.install(context)
        CrashLog.collectExitReasons(context, now)
        val report = CrashLog.pendingReport.value!!
        assertEquals(1, Regex("^CRASH", RegexOption.MULTILINE).findAll(report).count())
    }

    @Test fun printableKeepsReadableRuns() {
        val runs = CrashLog.printable(byteArrayOf(1, 2) + "libc.so abort".toByteArray() + byteArrayOf(0) + "ab".toByteArray())
        assertEquals(listOf("libc.so abort"), runs)
    }
}
