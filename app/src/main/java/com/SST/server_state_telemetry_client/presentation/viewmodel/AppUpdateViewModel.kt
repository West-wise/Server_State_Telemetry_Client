package com.SST.server_state_telemetry_client.presentation.viewmodel

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SST.server_state_telemetry_client.data.repository.AppUpdateInstaller
import com.SST.server_state_telemetry_client.data.repository.AppUpdateRepository
import com.SST.server_state_telemetry_client.domain.model.AppUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface AppUpdateState {
    val update: AppUpdate
    data class Available(override val update: AppUpdate) : AppUpdateState
    data class Downloading(override val update: AppUpdate, val bytes: Long = 0, val total: Long? = null) : AppUpdateState
    data class Ready(override val update: AppUpdate, val apk: File) : AppUpdateState
    data class Failed(override val update: AppUpdate) : AppUpdateState
}

@HiltViewModel
internal class AppUpdateViewModel @Inject constructor(
    private val updates: AppUpdateRepository,
    private val installer: AppUpdateInstaller
) : ViewModel() {
    var state by mutableStateOf<AppUpdateState?>(null)
        private set
    var preparingInstall by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun check(installedCode: Long, sdk: Int) {
        if (state != null || job?.isActive == true) return
        job = viewModelScope.launch {
            state = updates.check(installedCode, sdk)?.let { AppUpdateState.Available(it) }
        }
    }

    fun download() {
        val update = state?.update ?: return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            state = AppUpdateState.Downloading(update)
            try {
                val file = installer.download(update) { bytes, total ->
                    withContext(Dispatchers.Main.immediate) { state = AppUpdateState.Downloading(update, bytes, total) }
                }
                state = AppUpdateState.Ready(update, file)
            } catch (_: TimeoutCancellationException) { state = AppUpdateState.Failed(update)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { state = AppUpdateState.Failed(update) }
        }
    }

    fun install(launch: (Intent) -> Unit) {
        val ready = state as? AppUpdateState.Ready ?: return
        if (preparingInstall) return
        job = viewModelScope.launch {
            preparingInstall = true
            try {
                launch(installer.installIntent(ready.apk, ready.update))
                // Keep Ready for retry if the user cancels the system installer.
                // Successful self-update replaces this process; the new version checks again on start.
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                ready.apk.delete()
                state = AppUpdateState.Failed(ready.update)
            } finally { preparingInstall = false }
        }
    }

    fun dismiss() {
        val previous = state ?: return
        job?.cancel()
        (previous as? AppUpdateState.Ready)?.apk?.delete()
        updates.dismiss(previous.update)
        state = null
    }
}
