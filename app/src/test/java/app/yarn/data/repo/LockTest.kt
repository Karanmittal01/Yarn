package app.yarn.data.repo

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LockTest {
    @Test
    fun anArrivingMessageDoesNotWaitForALongImport() = runTest {
        val lock = Mutex()
        val importing = CompletableDeferred<Unit>()
        val finishImport = CompletableDeferred<Unit>()
        // A long import holds the lock until told to stop.
        val import = launch { lock.lock(); importing.complete(Unit); finishImport.await(); lock.unlock() }
        importing.await()
        var stored = false
        lock.withLockOrAfter(3_000) { stored = true }
        assertThat(stored).isTrue()
        // It didn't take the lock, so it mustn't release the import's lock either.
        assertThat(lock.isLocked).isTrue()
        finishImport.complete(Unit)
        import.join()
        assertThat(lock.isLocked).isFalse()
    }

    @Test
    fun whenFreeTheLockIsHeldAndReleased() = runTest {
        val lock = Mutex()
        var heldInside = false
        lock.withLockOrAfter(3_000) { heldInside = lock.isLocked }
        assertThat(heldInside).isTrue()
        assertThat(lock.isLocked).isFalse()
    }
}
