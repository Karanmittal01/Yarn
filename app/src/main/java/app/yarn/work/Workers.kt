package app.yarn.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.yarn.YarnApplication
import app.yarn.data.db.MessageStatus
import app.yarn.notifications.Notifier
import java.util.concurrent.TimeUnit

private val Context.container get() = (applicationContext as YarnApplication).container

private fun foregroundInfo(context: Context, text: String): ForegroundInfo {
    val n = context.container.notifier.backgroundNotification(text)
    // Only shown on Android 10/11, where expedited work runs as a short foreground service.
    return ForegroundInfo(Notifier.BACKGROUND_ID, n)
}

/** Drains the outgoing queue. Needs no network constraint: SMS uses the cellular radio. */
class SendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.sender.processQueue()
        c.sender.reschedule()
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext, "Sending messages…")

    companion object {
        fun enqueue(context: Context, delayMillis: Long = 0) {
            val wm = WorkManager.getInstance(context)
            if (delayMillis <= 0) {
                wm.enqueueUniqueWork(
                    "send-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<SendWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build(),
                )
            } else {
                wm.enqueueUniqueWork(
                    "send-later", ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<SendWorker>().setInitialDelay(delayMillis, TimeUnit.MILLISECONDS).build(),
                )
            }
        }
    }
}

/** Retries a failed MMS download after a backoff; MMS needs mobile data, so require a network. */
class MmsDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val c = applicationContext.container
        val m = c.db.messages().get(id) ?: return Result.success()
        if (m.status == MessageStatus.DOWNLOAD_FAILED || m.status == MessageStatus.PENDING_DOWNLOAD) c.incoming.download(id)
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "id"
        fun enqueue(context: Context, messageId: Long, delayMillis: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "mms-download-$messageId", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MmsDownloadWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(KEY_ID to messageId))
                    .build(),
            )
        }
    }
}

/**
 * Imports the system message store, then analyses everything not yet analysed (newest first),
 * in small batches so the inbox stays responsive during a large first import.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.sync.sync()
        var processed = 0
        while (!isStopped) {
            val batch = c.db.messages().unanalyzed(200)
            if (batch.isEmpty()) break
            val touched = HashSet<Long>()
            for (m in batch) {
                val (updated, entities) = c.engine.annotate(m)
                c.db.messages().update(updated)
                c.db.messages().replaceEntities(m.id, "rules", entities)
                touched += m.conversationId
            }
            touched.forEach { c.db.conversations().refresh(it) }
            processed += batch.size
        }
        runCatching { c.smartSorter.sortRecent(limit = 60) }
        c.settings.update { it.copy(initialImportDone = true) }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext, "Organising your messages…")

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build(),
            )
        }
    }
}

/** Re-runs analysis for all messages after the user changes sensitivity or resets learning. */
class ReanalyzeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.engine.reload()
        var offset = 0
        val touched = HashSet<Long>()
        while (!isStopped) {
            val page = c.db.messages().page(500, offset)
            if (page.isEmpty()) break
            for (m in page) {
                if (m.outgoing) continue
                val (updated, entities) = c.engine.annotate(m)
                c.db.messages().update(updated)
                c.db.messages().replaceEntities(m.id, "rules", entities)
                touched += m.conversationId
            }
            offset += page.size
        }
        touched.forEach { c.db.conversations().refresh(it) }
        // Let the on-device model take a second look at whatever the rules were unsure about.
        if (!isStopped) runCatching { c.smartSorter.sortRecent(limit = 150) }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("reanalyze", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReanalyzeWorker>().build())
        }
    }
}

/** Daily housekeeping: OTP/spam retention, stuck states, provider reconciliation, temp files. */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.current()
        val now = System.currentTimeMillis()
        if (s.otpAutoDeleteHours > 0) {
            val ids = c.db.messages().otpOlderThan(now - s.otpAutoDeleteHours * 3_600_000L)
            if (ids.isNotEmpty()) c.conversations.deleteMessages(ids)
        }
        if (s.spamAutoDeleteDays > 0) {
            val ids = c.db.messages().spamOlderThan(now - s.spamAutoDeleteDays * 86_400_000L)
            if (ids.isNotEmpty()) c.conversations.deleteMessages(ids)
        }
        c.sender.processQueue()
        c.sync.sync()
        c.sync.reconcileDeletions()
        c.db.conversations().deleteEmpty()
        app.yarn.messaging.MediaTools.mmsDir(applicationContext).listFiles()?.forEach { f ->
            if (now - f.lastModified() > 86_400_000L) f.delete()
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "maintenance", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }
    }
}

/** Daily encrypted backup to the user's Google Drive, on Wi-Fi while charging. */
class GoogleBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.current()
        if (s.googleAccount == null || !s.autoBackup) return Result.success()
        // Only works silently when access was granted before; otherwise wait for the user.
        val token = runCatching { c.drive.token(s.googleAccount) }.getOrElse { return Result.success() }
        val result = c.backup.backupToGoogle(c.drive, token, s.backupMedia) ?: return if (runAttemptCount < 3) Result.retry() else Result.success()
        c.settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupBytes = result.first) }
        c.backup.reset()
        return Result.success()
    }

    companion object {
        private const val NAME = "google-backup"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            wm.enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GoogleBackupWorker>(24, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                            .setRequiresCharging(true)
                            .build(),
                    )
                    .build(),
            )
        }
    }
}
