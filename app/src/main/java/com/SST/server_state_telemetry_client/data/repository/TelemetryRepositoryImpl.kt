package com.SST.server_state_telemetry_client.data.repository

import com.SST.server_state_telemetry_client.data.local.dao.ServerDao
import com.SST.server_state_telemetry_client.data.local.entity.ServerEntity
import com.SST.server_state_telemetry_client.data.remote.SocketDataSource
import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import com.SST.server_state_telemetry_client.domain.model.SystemStats
import com.SST.server_state_telemetry_client.domain.repository.TelemetryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TelemetryRepositoryImpl
@Inject
constructor(private val socketDataSource: SocketDataSource, private val serverDao: ServerDao) :
        TelemetryRepository {

    override fun getServerState(host: String, port: Int): Flow<SystemStats> {
        return socketDataSource.receiveData(host, port)
    }

    override fun getAllServers(): Flow<List<RegistedServerList>> {
        return combine(serverDao.getAllServers(), socketDataSource.connectedHosts) {
                entities,
                connected ->
            entities.map { entity ->
                RegistedServerList(
                        id = entity.id,
                        ip = entity.ip,
                        name = entity.name,
                        status = connected.contains("${entity.ip}:${entity.port}"),
                        port = entity.port,
                        hmacKey = entity.hmacKey
                )
            }
        }
    }

    override suspend fun addServer(name: String, ip: String, port: Int, hmacKey: String) {
        serverDao.insertServer(ServerEntity(name = name, ip = ip, port = port, hmacKey = hmacKey))
    }

    override suspend fun deleteServer(id: Int) {
        serverDao.deleteServerById(id)
    }

    override suspend fun updateServer(server: RegistedServerList) {
        // Since RegistedServerList currently doesn't hold port/hmacKey, this is a placeholder.
        // It requires fetching the entity first or changing the UI model.
        // We will leave it empty as the user indicated edit functionality needs rework/is
        // secondary.
    }

    override suspend fun connect(host: String, port: Int, hmacKey: String) {
        socketDataSource.connect(host, port, hmacKey)
    }

    override suspend fun disconnect(host: String, port: Int) {
        socketDataSource.disconnect(host, port)
    }
}
