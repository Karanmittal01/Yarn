package app.yarn

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play In-App Updates (flexible): when Play has a newer build (e.g. pushed by CI to the
 * internal testing track), the user is offered it inside Yarn, it downloads in the background
 * while they keep messaging, and a "Restart to update" prompt finishes the install.
 * Only active for installs that came from Google Play; a no-op for sideloaded APKs.
 */
class InAppUpdates(private val activity: FragmentActivity) {
    private val manager = AppUpdateManagerFactory.create(activity)
    private val _readyToInstall = MutableStateFlow(false)
    val readyToInstall: StateFlow<Boolean> = _readyToInstall.asStateFlow()
    private var promptedThisSession = false

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _readyToInstall.value = true
    }

    fun register() = manager.registerListener(listener)
    fun unregister() = manager.unregisterListener(listener)

    /** Call on resume: resumes a finished download prompt or offers a newly available update. */
    fun check() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _readyToInstall.value = true
                !promptedThisSession &&
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    promptedThisSession = true
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
                }
            }
        }.addOnFailureListener { Log.d("InAppUpdates", "Update check unavailable (not installed from Play?)", it) }
    }

    fun completeUpdate() {
        manager.completeUpdate()
    }
}
