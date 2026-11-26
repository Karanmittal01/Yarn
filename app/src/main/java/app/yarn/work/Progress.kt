package app.yarn.work

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Long-running background jobs whose progress the UI shows as a bar. */
enum class JobKind {
    /** The first import's sorting. */
    SORTING,
    /** After a settings change (spam sensitivity, learning reset). */
    RESORTING,
    /** After an app update, with the new smart sorting. */
    UPDATING,
}

/** [total] = 0 means the size isn't known yet. */
data class JobProgress(val kind: JobKind, val done: Int, val total: Int, val complete: Boolean = false) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** Shared, observable progress for sorting work so every screen can show the same bar. */
class ProgressTracker {
    private val _job = MutableStateFlow<JobProgress?>(null)
    val job: StateFlow<JobProgress?> = _job.asStateFlow()

    fun update(kind: JobKind, done: Int, total: Int) { _job.value = JobProgress(kind, done, total) }

    /** Shows the job as finished ("All messages re-sorted") until [finish]. */
    fun complete(kind: JobKind, total: Int) { _job.value = JobProgress(kind, total, total, complete = true) }
    fun finish(kind: JobKind) { if (_job.value?.kind == kind) _job.value = null }
}
