package com.SST.server_state_telemetry_client.domain.repository

import com.SST.server_state_telemetry_client.domain.model.ServerState
import kotlinx.coroutines.flow.Flow

interface TelemetryRepository {
    fun getServerState(): Flow<ServerState>
    suspend fun connect(host: String, port: Int)
    suspend fun disconnect()
}
