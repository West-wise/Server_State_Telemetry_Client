package com.SST.server_state_telemetry_client.data.remote

import com.SST.server_state_telemetry_client.domain.model.SystemStats
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketDataSource @Inject constructor() {

    data class ConnectionSession(
        val socket: Socket,
        val inputChannel: ByteReadChannel,
        var requestIdCounter: AtomicInteger = AtomicInteger(1)
    )

    private val sockets = mutableMapOf<String, ConnectionSession>()
    private val mutex = Mutex()
    private val hostMutexes = ConcurrentHashMap<String, Mutex>()
    private val selectorManager = SelectorManager(Dispatchers.IO)

    private val _connectedHosts = MutableStateFlow<Set<String>>(emptySet())
    val connectedHosts: StateFlow<Set<String>> = _connectedHosts.asStateFlow()

    /**
     * 지정된 호스트에 일반 TCP 소켓 연결을 맺고 인증 패킷(CMD_AUTH)을 전송합니다.
     * SSTD 프로토콜 변경에 따라 40바이트 헤더 및 SipHash-2-4 128비트 서명을 사용합니다.
     */
    suspend fun connect(host: String, port: Int, hashKey: String) {
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
                    // SSTD 사양에 맞춰 TLS 연결을 해제하고 일반 TCP 소켓으로 직접 연결
                    val newSocket = aSocket(selectorManager).tcp().connect(host, port)
                    val channel = newSocket.openReadChannel()
                    val writeChannel = newSocket.openWriteChannel(autoFlush = true)
                    val session = ConnectionSession(newSocket, channel)

                    // cmd_mask가 제거된 40바이트 SecureHeader 버퍼 할당
                    val header = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                    header.putInt(0x53535444) // magic
                    header.put(0x01.toByte()) // version
                    header.put(0x01.toByte()) // type: request
                    header.putShort(0.toShort()) // client_id
                    // cmd_mask(2바이트)가 제거됨에 따라 바로 request_id를 배치
                    header.putInt(session.requestIdCounter.getAndIncrement()) // request_id
                    header.putLong(System.currentTimeMillis()) // timestamp
                    header.putInt(0) // body_len
                    // auth_tag (16바이트) 자리는 0바이트로 패딩해 둔 뒤 해시 계산을 수행
                    val authPadding = ByteArray(16)
                    header.put(authPadding)

                    val currentBytes = header.array()
                    try {
                        val cleanKey = hashKey.trim()
                        // 16바이트 SipHash 키는 hex-encoded 스트링 기준으로 정확히 32자여야 함
                        if (cleanKey.length == 32) {
                            val keyBytes = cleanKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            // auth_tag를 0으로 채운 전체 40바이트 패킷에 대해 SipHash-2-4 128비트 해싱 수행
                            val authTag = SipHash.hash128(keyBytes, currentBytes)
                            // 계산된 16바이트 해시값을 헤더의 24~39 오프셋(auth_tag) 위치에 덮어씌움
                            System.arraycopy(authTag, 0, currentBytes, 24, 16)
                        } else {
                            android.util.Log.e(
                                "SocketDataSource",
                                "Invalid hash key length: ${cleanKey.length}"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "SocketDataSource",
                            "SipHash calculation failed: ${e.message}"
                        )
                    }

                    // 인증 패킷 40바이트를 소켓으로 전송
                    writeChannel.writeFully(currentBytes, 0, 40)

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

    /**
     * 소켓 채널로부터 원격 서버 상태 텔레메트리 패킷을 지속적으로 수신하여 파싱합니다.
     * SSTD 프로토콜 변경에 따라 40바이트 헤더 구조에 맞추어 디코딩을 수행합니다.
     */
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
                    // cmd_mask가 제거된 새로운 40바이트 규격의 SecureHeader를 수신
                    val headerBytes = ByteArray(40)
                    ch.readFully(headerBytes, 0, 40)
                    val headerBuf = java.nio.ByteBuffer.wrap(headerBytes)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)

                    val magic = headerBuf.int
                    if (magic != 0x53535444) {
                        // 유효하지 않은 매직넘버 유입 시 루프를 통해 다음 스트림 검색
                        continue
                    }

                    val version = headerBuf.get()
                    val type = headerBuf.get()
                    val clientId = headerBuf.short
                    // cmd_mask(2바이트)가 제거되었으므로 즉시 request_id와 timestamp, bodyLen을 파싱
                    val requestId = headerBuf.int
                    val timestamp = headerBuf.long
                    val bodyLen = headerBuf.int
                    val authTag = ByteArray(16)
                    headerBuf.get(authTag)

                    android.util.Log.d(
                        "SocketDataSource",
                        "Header parsed from $host:$port -> Magic: ${magic.toString(16)}, type: ${type.toInt()}, bodyLen: $bodyLen"
                    )

                    // SystemStat 메시지 타입(0x11) 및 규격 크기(134바이트) 검증
                    if (type.toInt() == 0x11 && bodyLen == 134) {
                        // SystemStats 134바이트 본문 데이터를 수신
                        val bodyBytes = ByteArray(134)
                        ch.readFully(bodyBytes, 0, 134)
                        val bodyBuf = java.nio.ByteBuffer.wrap(bodyBytes)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN)

                        val validMask = bodyBuf.short.toInt() and 0xFFFF
                        bodyBuf.short // reserved 영역 스킵
                        val cpuUsage = bodyBuf.get().toInt() and 0xFF
                        val memUsage = bodyBuf.get().toInt() and 0xFF

                        // 수신/송신 네트워크 정보 파싱
                        val rxBytesPs = bodyBuf.long
                        val rxPacketPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val rxErrPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val rxDropPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val netRx = com.SST.server_state_telemetry_client.domain.model.NetInfo(
                            rxBytesPs,
                            rxPacketPs,
                            rxErrPs,
                            rxDropPs
                        )

                        val txBytesPs = bodyBuf.long
                        val txPacketPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val txErrPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val txDropPs = bodyBuf.int.toLong() and 0xFFFFFFFFL
                        val netTx = com.SST.server_state_telemetry_client.domain.model.NetInfo(
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
                        val fdInfo = com.SST.server_state_telemetry_client.domain.model.FdInfo(
                            allocatedFdCnt,
                            usingFdCnt
                        )

                        // 각 마운트별 디스크 사용량 분석 및 기가바이트(GB) 매핑을 위한 데이터
                        val totalRoot = bodyBuf.long
                        val usedRoot = bodyBuf.long
                        val totalHome = bodyBuf.long
                        val usedHome = bodyBuf.long
                        val totalVar = bodyBuf.long
                        val usedVar = bodyBuf.long
                        val totalBoot = bodyBuf.long
                        val usedBoot = bodyBuf.long
                        val diskSummary = com.SST.server_state_telemetry_client.domain.model.DiskSummary(
                            totalRoot,
                            usedRoot,
                            totalHome,
                            usedHome,
                            totalVar,
                            usedVar,
                            totalBoot,
                            usedBoot
                        )

                        val stats = com.SST.server_state_telemetry_client.domain.model.SystemStats(
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
                        // 미정의 바디 페이로드 유입 시 안전하게 건너뛰도록 오프셋 처리
                        android.util.Log.w(
                            "SocketDataSource",
                            "Skipping unknown payload from $host:$port, length: $bodyLen, type: ${type.toInt()}"
                        )
                        val skipBytes = ByteArray(bodyLen)
                        ch.readFully(skipBytes, 0, bodyLen)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "SocketDataSource",
                    "Socket error or disconnected from $host:$port: ${e.message}"
                )
                e.printStackTrace()
                mutex.withLock { _connectedHosts.value = _connectedHosts.value - "$host:$port" }
            }
            kotlinx.coroutines.delay(1000L)
        }
    }
}
