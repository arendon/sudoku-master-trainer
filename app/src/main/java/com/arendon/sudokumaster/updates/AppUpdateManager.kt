package com.arendon.sudokumaster.updates

import android.content.Context
import com.arendon.sudokumaster.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appdistribution.FirebaseAppDistribution
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class UpdateCheckOutcome {
    data object Unavailable : UpdateCheckOutcome()
    data class PlayStoreManaged(val message: String) : UpdateCheckOutcome()
    data class Completed(val message: String) : UpdateCheckOutcome()
    data class Failed(val reason: String) : UpdateCheckOutcome()
}

/**
 * Checks Firebase App Distribution for newer releases in the `directApk` flavor while
 * short-circuiting cleanly on `playBundle` or unconfigured local debug builds.
 */
class AppUpdateManager(private val context: Context) {

    fun isFirebaseConfigured(): Boolean {
        return runCatching {
            FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun checkForUpdates(): UpdateCheckOutcome {
        if (BuildConfig.DISTRIBUTION_CHANNEL == "PLAY_BUNDLE") {
            return UpdateCheckOutcome.PlayStoreManaged(
                "Installed via Google Play (Advanced Protection safe). Updates arrive automatically through Google Play."
            )
        }

        if (!isFirebaseConfigured()) {
            return UpdateCheckOutcome.Unavailable
        }

        return suspendCancellableCoroutine { continuation ->
            runCatching {
                val appDistribution = FirebaseAppDistribution.getInstance()
                appDistribution.updateIfNewReleaseAvailable()
                    .addOnSuccessListener {
                        if (continuation.isActive) {
                            continuation.resume(
                                UpdateCheckOutcome.Completed("Checked Firebase App Distribution for newer releases.")
                            )
                        }
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) {
                            continuation.resume(
                                UpdateCheckOutcome.Failed(
                                    error.localizedMessage ?: "Unable to check for updates right now."
                                )
                            )
                        }
                    }
            }.onFailure { error ->
                if (continuation.isActive) {
                    continuation.resume(
                        UpdateCheckOutcome.Failed(error.localizedMessage ?: "Update service unavailable")
                    )
                }
            }
        }
    }
}
