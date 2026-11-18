package app.yarn

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        NativeGuard.install(context, versionCode = 3700)
        NativeGuard.reset()
    }

    private fun exit(reason: Int, at: Long, description: String? = null, trace: ByteArray? = null) =
        am.addApplicationExitInfo(
            ApplicationExitInfoBuilder.newBuilder()
                .setReason(reason).setTimestamp(at).setProcessName(context.packageName).setDescription(description)
                .setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED).setPss(180_000).setRss(240_000)
                .apply { trace?.let { setTraceInputStream(it.inputStream()) } }
                .build(),
        )

    private fun runs(vararg parts: String): ByteArray = parts.fold(byteArrayOf()) { acc, p -> acc + p.toByteArray() + byteArrayOf(0x12, 0x00) }

    // Shaped like the real tombstone from the Galaxy S25 Ultra: waiting threads, then the crashing one.
    private val smartReplyTombstone = runs(
        "SIGSEGV", "SEGV_MAPERR@", "binder:5398_2", "android::IPCThreadState::talkWithDriver(bool)(", "DefaultDispatch",
        "pool-6-thread-3",
        "tflite::ops::builtin::reduce::GetOutputShape(TfLiteContext*)(",
        "2~/data/app/~~abc==/app.yarn.messages-xyz==/split_config.arm64_v8a.apk!libpredictor_jni.so8",
        "hobbes::mlkit::SmartReplyPredictor::Predict(",
        "EJava_com_google_mlkit_nl_smartreply_jni_PredictorJni_predictHobbesJNI(",
        "HeapTaskDaemon", "art::gc::TaskProcessor::RunAllTasks(art::Thread*)(",
    )

    @Test fun nativeCrashNamesTheLibraryAndSwitchesItsFeatureOff() {
        exit(ApplicationExitInfo.REASON_CRASH_NATIVE, now - 3_600_000, "crash", smartReplyTombstone)
        CrashLog.collectExitReasons(context, now)
        val report = CrashLog.pendingReport.value!!
        assertTrue(report, report.contains("Native crash in the background"))
        assertTrue(report, report.contains("Crashed in libpredictor_jni.so"))
        assertTrue(report, report.contains("SmartReplyPredictor::Predict"))
        assertTrue(report, report.contains("Turned off until the next update: Suggested replies"))
        assertTrue(report, report.contains("memory 175 MB"))
        // The waiting threads aren't worth reading.
        assertFalse(report, report.contains("HeapTaskDaemon"))
        assertFalse(NativeGuard.allowed(NativeGuard.Feature.SMART_REPLY))
        assertTrue(NativeGuard.allowed(NativeGuard.Feature.TRANSLATE))
    }

    @Test fun switchedOffFeaturesComeBackAfterAnUpdate() {
        NativeGuard.disable(NativeGuard.Feature.TRANSLATE)
        NativeGuard.install(context, versionCode = 3700)
        assertFalse(NativeGuard.allowed(NativeGuard.Feature.TRANSLATE))
        NativeGuard.install(context, versionCode = 3800)
        assertTrue(NativeGuard.allowed(NativeGuard.Feature.TRANSLATE))
    }

    @Test fun nativeCrashFiledTwiceIsReportedOnce() {
        // Android records a native crash as a native crash and again as a plain "crash" with no detail.
        exit(ApplicationExitInfo.REASON_CRASH, now - 60_000, "crash")
        exit(ApplicationExitInfo.REASON_CRASH_NATIVE, now - 60_400, "crash", smartReplyTombstone)
        CrashLog.collectExitReasons(context, now)
        val report = CrashLog.pendingReport.value!!
        assertEquals(1, Regex("^CRASH", RegexOption.MULTILINE).findAll(report).count())
        assertTrue(report.contains("Native crash"))
    }

    @Test fun manyCrashesAreCapped() {
        repeat(9) { exit(ApplicationExitInfo.REASON_CRASH_NATIVE, now - (it + 1) * 60_000L, "crash", smartReplyTombstone) }
        CrashLog.collectExitReasons(context, now)
        val report = CrashLog.pendingReport.value!!
        assertEquals(6, Regex("^CRASH", RegexOption.MULTILINE).findAll(report).count())
        assertTrue(report.contains("and 3 earlier"))
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
