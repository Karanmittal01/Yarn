package app.yarn.work

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Long-running background jobs whose progress the UI shows as a bar. */
enum class JobKind { SORTING, RESORTING }

/** [total] = 0 means the size isn't known yet. */
data class JobProgress(val kind: JobKind, val done: Int, val total: Int) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** Shared, observable progress for sorting work so every screen can show the same bar. */
class ProgressTracker {
    private val _job = MutableStateFlow<JobProgress?>(null)
    val job: StateFlow<JobProgress?> = _job.asStateFlow()

    fun update(kind: JobKind, done: Int, total: Int) { _job.value = JobProgress(kind, done, total) }
    fun finish(kind: JobKind) { if (_job.value?.kind == kind) _job.value = null }
}
