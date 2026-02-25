package com.SST.server_state_telemetry_client.data.remote

import com.SST.server_state_telemetry_client.domain.model.SystemStats
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class SocketDataSource @Inject constructor() {

    data class ConnectionSession(val socket: Socket, val inputChannel: ByteReadChannel)

    private val sockets = mutableMapOf<String, ConnectionSession>()
    private val mutex = Mutex()
    private val hostMutexes = ConcurrentHashMap<String, Mutex>()
    private val selectorManager = SelectorManager(Dispatchers.IO)

    private val _connectedHosts = MutableStateFlow<Set<String>>(emptySet())
    val connectedHosts: StateFlow<Set<String>> = _connectedHosts.asStateFlow()

    suspend fun connect(host: String, port: Int, hmacKey: String) {
        val key = "$host:$port"
        android.util.Log.d("SocketDataSource", "Attempting connection to $key")

        val hostMutex = hostMutexes.getOrPut(key) { Mutex() }
        hostMutex.withLock {
            mutex.withLock {
                val existingSession = sockets[key]
                if (existingSession != null && !existingSession.inputChannel.isClosedForRead) {
                    android.util.Log.d("SocketDataSource", "Connection to $key already active.")
                    return
                }
            }

            withContext(Dispatchers.IO) {
                try {
                    val newSocket =
                            aSocket(selectorManager).tcp().connect(host, port).tls(Dispatchers.IO) {
                                serverName = host
                            }
                    val channel = newSocket.openReadChannel()
                    val writeChannel = newSocket.openWriteChannel(autoFlush = true)

                    // Construct CMD_AUTH (0x0001) Secure Header
                    val header = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN)
                    header.putInt(0x53535444) // magic
                    header.put(0x01.toByte()) // version
                    header.put(0x01.toByte()) // type: request
                    header.putShort(0.toShort()) // client_id
                    header.putShort(0x0001.toShort()) // cmd_mask (CMD_AUTH)
                    header.putInt(1) // request_id
                    header.putLong(System.currentTimeMillis()) // timestamp
                    header.putInt(0) // body_len

                    val currentBytes = header.array()
                    try {
                        val cleanKey = hmacKey.trim()
                        if (cleanKey.length == 64) {
                            val keyBytes =
                                    cleanKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            val mac = Mac.getInstance("HmacSHA256")
                            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
                            val authTag = mac.doFinal(currentBytes)
                            System.arraycopy(authTag, 0, currentBytes, 26, 16)
                        } else {
                            android.util.Log.e(
                                    "SocketDataSource",
                                    "Invalid HMAC key length: ${cleanKey.length}"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "SocketDataSource",
                                "HMAC calculation failed: ${e.message}"
                        )
                    }

                    // Send Auth Packet
                    writeChannel.writeFully(currentBytes, 0, 42)

                    val session = ConnectionSession(newSocket, channel)

                    mutex.withLock {
                        sockets[key]?.socket?.close()
                        sockets[key] = session
                        _connectedHosts.value = _connectedHosts.value + key
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                            "SocketDataSource",
                            "Failed to connect to $key: ${e.message}"
                    )
                }
            }
        }
    }

    suspend fun disconnect(host: String, port: Int) {
        val key = "$host:$port"
        val hostMutex = hostMutexes.getOrPut(key) { Mutex() }
        hostMutex.withLock {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    sockets[key]?.socket?.close()
                    sockets.remove(key)
                    _connectedHosts.value = _connectedHosts.value - key
                }
            }
        }
    }

    fun receiveData(host: String, port: Int): Flow<SystemStats> = flow {
        while (true) {
            val channel = mutex.withLock { sockets["$host:$port"]?.inputChannel }

            if (channel == null || channel.isClosedForRead) {
                android.util.Log.w(
                        "SocketDataSource",
                        "receiveData loop for $host:$port waiting... Channel is null or closed"
                )
                kotlinx.coroutines.delay(1000L)
                continue
            }

            val ch = channel
            try {
                while (!ch.isClosedForRead) {
                    // Read SecureHeader (42 bytes)
                    val headerBytes = ByteArray(42)
                    ch.readFully(headerBytes, 0, 42)
                    val headerBuf =
                            java.nio.ByteBuffer.wrap(headerBytes)
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)

                    val magic = headerBuf.int
                    if (magic != 0x53535444) {
                        // Invalid magic number, maybe log and continue/break?
                        continue
                    }

                    val version = headerBuf.get()
                    val type = headerBuf.get()
                    val clientId = headerBuf.short
                    val cmdMask = headerBuf.short
                    val requestId = headerBuf.int
                    val timestamp = headerBuf.long
                    val bodyLen = headerBuf.int
                    val authTag = ByteArray(16)
                    headerBuf.get(authTag)

                    android.util.Log.d(
                            "SocketDataSource",
                            "Header parsed from $host:$port -> Magic: ${magic.toString(16)}, type: ${type.toInt()}, bodyLen: $bodyLen"
                    )

                    // If it's a SystemStat message (0x11)
                    if (type.toInt() == 0x11 && bodyLen == 134) {
                        // Read SystemStats (134 bytes)
                        val bodyBytes = ByteArray(134)
                        ch.readFully(bodyBytes, 0, 134)
                        val bodyBuf =
                                java.nio.ByteBuffer.wrap(bodyBytes)
                                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)

                        val validMask = bodyBuf.short.toInt() and 0xFFFF
                        bodyBuf.short // reserved
                        val cpuUsage = bodyBuf.get().toInt() and 0xFF
                        val memUsage = bodyBuf.get().toInt() and 0xFF

                        val rxBytesPs = bodyBuf.long
                        val rxPacketPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val rxErrPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val rxDropPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val netRx =
                                com.SST.server_state_telemetry_client.domain.model.NetInfo(
                                        rxBytesPs,
                                        rxPacketPs,
                                        rxErrPs,
                                        rxDropPs
                                )

                        val txBytesPs = bodyBuf.long
                        val txPacketPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val txErrPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val txDropPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val netTx =
                                com.SST.server_state_telemetry_client.domain.model.NetInfo(
                                        txBytesPs,
                                        txPacketPs,
                                        txErrPs,
                                        txDropPs
                                )

                        val procCount = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val totalProcCount = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val netUserCount = bodyBuf.short.toInt() and 0xFFFF
                        val connectedUserCount = bodyBuf.short.toInt() and 0xFFFF
                        val uptimeSecs = bodyBuf.int.toLong() and 0xFFFFFFFFL

                        val allocatedFdCnt = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val usingFdCnt = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val fdInfo =
                                com.SST.server_state_telemetry_client.domain.model.FdInfo(
                                        allocatedFdCnt,
                                        usingFdCnt
                                )

                        val totalRoot = bodyBuf.long
                        val usedRoot = bodyBuf.long
                        val totalHome = bodyBuf.long
                        val usedHome = bodyBuf.long
                        val totalVar = bodyBuf.long
                        val usedVar = bodyBuf.long
                        val totalBoot = bodyBuf.long
                        val usedBoot = bodyBuf.long
                        val diskSummary =
                                com.SST.server_state_telemetry_client.domain.model.DiskSummary(
                                        totalRoot,
                                        usedRoot,
                                        totalHome,
                                        usedHome,
                                        totalVar,
                                        usedVar,
                                        totalBoot,
                                        usedBoot
                                )

                        val stats =
                                com.SST.server_state_telemetry_client.domain.model.SystemStats(
                                        validMask = validMask,
                                        cpuUsage = cpuUsage,
                                        memUsage = memUsage,
                                        netRx = netRx,
                                        netTx = netTx,
                                        procCount = procCount,
                                        totalProcCount = totalProcCount,
                                        netUserCount = netUserCount,
                                        connectedUserCount = connectedUserCount,
                                        uptimeSecs = uptimeSecs,
                                        fdInfo = fdInfo,
                                        disk = diskSummary
                                )
                        android.util.Log.d(
                                "SocketDataSource",
                                "Parsed SystemStats from $host:$port: CPU=$cpuUsage, RAM=$memUsage, Rx=${netRx.bytePerSec}, Tx=${netTx.bytePerSec}, users=$connectedUserCount"
                        )
                        emit(stats)
                    } else if (bodyLen > 0) {
                        // Skip unknown payload
                        android.util.Log.w(
                                "SocketDataSource",
                                "Skipping unknown payload from $host:$port, length: $bodyLen, type: ${type.toInt()}"
                        )
                        val skipBytes = ByteArray(bodyLen)
                        ch.readFully(skipBytes, 0, bodyLen)
                    }
                }
            } catch (e: Exception) {
                // Connection closed or EOF, handle gracefully
                android.util.Log.e(
                        "SocketDataSource",
                        "Socket error or disconnected from $host:$port: ${e.message}"
                )
                e.printStackTrace()
                mutex.withLock { _connectedHosts.value = _connectedHosts.value - "$host:$port" }
            }
            kotlinx.coroutines.delay(1000L) // Delay before trying to fetch the channel again
        }
    }
}
