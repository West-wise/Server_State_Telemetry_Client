package com.SST.server_state_telemetry_client.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import com.SST.server_state_telemetry_client.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(private val repository: TelemetryRepository) : ViewModel() {

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()
    val servers: StateFlow<List<RegistedServerList>> =
            repository
                    .getAllServers()
                    .stateIn(
                            scope = viewModelScope,
                            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                            initialValue = emptyList()
                    )

    init {
        viewModelScope.launch {
            try {
                // Auto-connect to all registered servers on startup
                val initialServers = repository.getAllServers().first()
                initialServers.forEach { server -> connect(server.ip, server.port, server.hmacKey) }
            } catch (e: Exception) {
                // Ignore exceptions during auto-connect
            }
        }
    }

    fun getServerState(
            host: String,
            port: Int
    ): kotlinx.coroutines.flow.Flow<
            com.SST.server_state_telemetry_client.domain.model.SystemStats> {
        return repository.getServerState(host, port)
    }

    fun connect(host: String, port: Int, hmacKey: String = "") {
        viewModelScope.launch {
            try {
                repository.connect(host, port, hmacKey)
                _connectionStatus.value = "Connected to $host:$port"
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    // TODO(modified): 서버 추가 (id 유니크 보장)
    fun addServer(
            name: String,
            ip: String,
            status: Boolean = false,
            port: Int = 443,
            hmacKey: String = ""
    ) {
        val safeName = name.trim()
        val safeIp = ip.trim()

        if (safeName.isBlank() || safeIp.isBlank()) return

        viewModelScope.launch {
            repository.addServer(name = safeName, ip = safeIp, port = port, hmacKey = hmacKey)
            // Attempt auto-connect upon adding
            connect(safeIp, port, hmacKey)
        }
    }

    fun deleteServer(id: Int) {
        val targetServer = servers.value.find { it.id == id }
        if (targetServer != null) {
            viewModelScope.launch {
                try {
                    repository.disconnect(targetServer.ip, targetServer.port)
                } catch (e: Exception) {
                    _connectionStatus.value = "Disconnect Error: ${e.message}"
                }
                repository.deleteServer(id)
            }
        }
    }

    fun editServer(
            id: Int,
            name: String,
            ip: String,
            status: Boolean? = null,
            port: Int? = null,
            hmacKey: String? = null
    ) {
        val safeName = name.trim()
        val safeIp = ip.trim()
        if (safeName.isBlank() || safeIp.isBlank()) return

        val targetServer = servers.value.find { it.id == id }
        if (targetServer != null) {
            viewModelScope.launch {
                repository.updateServer(
                        targetServer.copy(
                                name = safeName,
                                ip = safeIp,
                                status = status ?: targetServer.status,
                                port = port ?: targetServer.port,
                                hmacKey = hmacKey ?: targetServer.hmacKey
                        )
                )
            }
        }
    }
}
