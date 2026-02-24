package com.SST.server_state_telemetry_client.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import com.SST.server_state_telemetry_client.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TelemetryRepository
) : ViewModel() {

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()
    private val _servers = MutableStateFlow<List<RegistedServerList>>(emptyList())
    val servers: StateFlow<List<RegistedServerList>> = _servers.asStateFlow()

    fun connect(host: String, port: Int) {
        viewModelScope.launch {
            try {
                repository.connect(host, port)
                _connectionStatus.value = "Connected to $host:$port"
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    // TODO(modified): 서버 추가 (id 유니크 보장)
    fun addServer(name: String, ip: String, status: Boolean = false) {
        val safeName = name.trim()
        val safeIp = ip.trim()

        if (safeName.isBlank() || safeIp.isBlank()) return

        val current = _servers.value
        val newId = (current.maxOfOrNull { it.id } ?: 0) + 1

        _servers.value = current + RegistedServerList(
            id = newId,
            ip = safeIp,
            name = safeName,
            status = status
        )
    }

    fun deleteServer(id: Int) {
        _servers.update { list ->
            list.filter { it.id != id } // TODO(modified)
        }
    }

    fun editServer(id: Int, name: String, ip: String, status: Boolean? = null){
        val safeName = name.trim()
        val safeIp = ip.trim()
        if( safeName.isBlank() || safeIp.isBlank()) return

        _servers.update { list ->
            list.map { s ->
                if(s.id == id){
                    s.copy(
                        name = safeName,
                        ip = safeIp,
                        status = status ?: s.status
                    )
                } else s
            }
        }
    }
}
