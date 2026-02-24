package com.SST.server_state_telemetry_client.data.remote

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketDataSource @Inject constructor() {

    private var socket: Socket? = null
    private var inputChannel: ByteReadChannel? = null

    suspend fun connect(host: String, port: Int) {
        withContext(Dispatchers.IO) {
            val selectorManager = SelectorManager(Dispatchers.IO)
            socket = aSocket(selectorManager).tcp().connect(host, port)
            inputChannel = socket?.openReadChannel()
        }
    }

    fun receiveData(): Flow<String> = flow {
        // Implement reading logic here
        // For now, just a placeholder structure
        inputChannel?.let { channel ->
            while (!channel.isClosedForRead) {
                // val line = channel.readUTF8Line()
                // if (line != null) emit(line)
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            socket?.close()
            socket = null
            inputChannel = null
        }
    }
}
