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
                        hashKey = entity.hashKey
                )
            }
        }
    }

    override suspend fun addServer(name: String, ip: String, port: Int, hashKey: String) {
        serverDao.insertServer(ServerEntity(name = name, ip = ip, port = port, hashKey = hashKey))
    }

    override suspend fun deleteServer(id: Int) {
        serverDao.deleteServerById(id)
    }

    override suspend fun updateServer(server: RegistedServerList) {
        // RegistedServerList 모델의 정보를 기반으로 로컬 데이터베이스의 서버 정보를 수정함
        serverDao.updateServer(
            ServerEntity(
                id = server.id,
                name = server.name,
                ip = server.ip,
                port = server.port,
                hashKey = server.hashKey
            )
        )
    }

    override suspend fun connect(host: String, port: Int, hashKey: String) {
        socketDataSource.connect(host, port, hashKey)
    }

    override suspend fun disconnect(host: String, port: Int) {
        socketDataSource.disconnect(host, port)
    }
}
