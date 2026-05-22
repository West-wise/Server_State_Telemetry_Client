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
                // 앱 기동 시 등록된 모든 서버에 자동 소켓 연결을 시도함 (SSTD 사양 변경)
                val initialServers = repository.getAllServers().first()
                initialServers.forEach { server -> connect(server.ip, server.port, server.hashKey) }
            } catch (e: Exception) {
                // 자동 연결 시 발생하는 오류는 무시함
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

    fun connect(host: String, port: Int, hashKey: String = "") {
        viewModelScope.launch {
            try {
                repository.connect(host, port, hashKey)
                _connectionStatus.value = "Connected to $host:$port"
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    // 서버를 로컬 데이터베이스에 새로 등록하는 함수
    fun addServer(
            name: String,
            ip: String,
            status: Boolean = false,
            port: Int = 443,
            hashKey: String = ""
    ) {
        val safeName = name.trim()
        val safeIp = ip.trim()

        if (safeName.isBlank() || safeIp.isBlank()) return

        viewModelScope.launch {
            repository.addServer(name = safeName, ip = safeIp, port = port, hashKey = hashKey)
            // 서버 등록 즉시 연결을 자동 시도함
            connect(safeIp, port, hashKey)
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
            hashKey: String? = null
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
                                hashKey = hashKey ?: targetServer.hashKey
                        )
                )
            }
        }
    }
}
