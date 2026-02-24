package com.SST.server_state_telemetry_client.data.repository

import com.SST.server_state_telemetry_client.data.remote.SocketDataSource
import com.SST.server_state_telemetry_client.domain.model.ServerState
import com.SST.server_state_telemetry_client.domain.repository.TelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TelemetryRepositoryImpl @Inject constructor(
    private val socketDataSource: SocketDataSource
) : TelemetryRepository {

    override fun getServerState(): Flow<ServerState> {
        return socketDataSource.receiveData().map { rawData ->
            // TODO: Parse the raw data into ServerState properly
            // Current placeholder implementation
            ServerState(0f, 0f, 0f, 0f, 0L)
        }
    }

    override suspend fun connect(host: String, port: Int) {
        socketDataSource.connect(host, port)
    }

    override suspend fun disconnect() {
        socketDataSource.disconnect()
    }
}
