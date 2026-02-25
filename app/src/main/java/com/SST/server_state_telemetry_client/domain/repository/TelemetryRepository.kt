package com.SST.server_state_telemetry_client.domain.repository

import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import com.SST.server_state_telemetry_client.domain.model.SystemStats
import kotlinx.coroutines.flow.Flow

interface TelemetryRepository {
    fun getServerState(host: String, port: Int): Flow<SystemStats>
    fun getAllServers(): Flow<List<RegistedServerList>>
    suspend fun addServer(name: String, ip: String, port: Int, hmacKey: String)
    suspend fun deleteServer(id: Int)
    suspend fun updateServer(server: RegistedServerList)
    suspend fun connect(host: String, port: Int, hmacKey: String)
    suspend fun disconnect(host: String, port: Int)
}
