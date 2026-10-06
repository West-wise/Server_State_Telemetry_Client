package com.SST.server_state_telemetry_client.data.remote

import com.SST.server_state_telemetry_client.domain.model.SystemStats
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketDataSource @Inject constructor() {

    data class ConnectionSession(
        val socket: Socket,
        val inputChannel: ByteReadChannel,
        val noiseSession: NoiseSession
    )

    private val sockets = mutableMapOf<String, ConnectionSession>()
    private val mutex = Mutex()
    private val hostMutexes = ConcurrentHashMap<String, Mutex>()
    private val selectorManager = SelectorManager(Dispatchers.IO)

    private val _connectedHosts = MutableStateFlow<Set<String>>(emptySet())
    val connectedHosts: StateFlow<Set<String>> = _connectedHosts.asStateFlow()

    /**
     * TCP 연결 후 Noise XX 핸드셰이크를 수행한다.
     * pubKey: 서버 정적 X25519 공개키 (64자리 hex = 32바이트).
     * 핀닝 불일치 또는 핸드셰이크 실패 시 연결이 수립되지 않는다.
     */
    suspend fun connect(host: String, port: Int, pubKey: String) {
        val key = "$host:$port"
        android.util.Log.d("SocketDataSource", "Connecting to $key")

        val pinnedPub = try {
            pubKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            android.util.Log.e("SocketDataSource", "Invalid pubKey hex for $key")
            return
        }
        if (pinnedPub.size != 32) {
            android.util.Log.e("SocketDataSource", "pubKey must be 32 bytes, got ${pinnedPub.size}")
            return
        }

        val hostMutex = hostMutexes.getOrPut(key) { Mutex() }
        hostMutex.withLock {
            mutex.withLock {
                val existing = sockets[key]
                if (existing != null && !existing.inputChannel.isClosedForRead) {
                    android.util.Log.d("SocketDataSource", "Already connected to $key")
                    return
                }
            }

            withContext(Dispatchers.IO) {
                try {
                    val newSocket = aSocket(selectorManager).tcp().connect(host, port)
                    val channel = newSocket.openReadChannel()
                    val writeChannel = newSocket.openWriteChannel(autoFlush = true)
                    val noiseSession = NoiseSession()

                    val ok = withTimeout(10_000L) {
                        noiseSession.handshakeClient(channel, writeChannel, pinnedPub)
                    }

                    if (!ok) {
                        newSocket.close()
                        android.util.Log.e("SocketDataSource", "Handshake failed for $key (key mismatch or crypto error)")
                        return@withContext
                    }

                    mutex.withLock {
                        sockets[key]?.socket?.close()
                        sockets[key] = ConnectionSession(newSocket, channel, noiseSession)
                        _connectedHosts.value = _connectedHosts.value + key
                    }
                    android.util.Log.d("SocketDataSource", "Handshake complete for $key")
                } catch (e: Exception) {
                    android.util.Log.e("SocketDataSource", "Connect failed for $key: ${e.message}")
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
     * 전송 단계 프레임 [4B LE 길이][암호문] 을 수신·복호화·파싱하여 SystemStats 를 방출한다.
     */
    fun receiveData(host: String, port: Int): Flow<SystemStats> = flow {
        while (true) {
            val session = mutex.withLock { sockets["$host:$port"] }

            if (session == null || session.inputChannel.isClosedForRead) {
                android.util.Log.w("SocketDataSource", "receiveData: no active session for $host:$port, waiting")
                kotlinx.coroutines.delay(1000L)
                continue
            }

            val ch = session.inputChannel
            val noise = session.noiseSession

            try {
                while (!ch.isClosedForRead) {
                    // 프레임 길이 (4B LE)
                    val lenBuf = ByteArray(4)
                    ch.readFully(lenBuf)
                    val ctLen = (lenBuf[0].toInt() and 0xff) or
                        ((lenBuf[1].toInt() and 0xff) shl 8) or
                        ((lenBuf[2].toInt() and 0xff) shl 16) or
                        ((lenBuf[3].toInt() and 0xff) shl 24)

                    if (ctLen <= 0 || ctLen > 65535) {
                        android.util.Log.w("SocketDataSource", "Invalid frame length $ctLen from $host:$port")
                        break
                    }

                    val ct = ByteArray(ctLen)
                    ch.readFully(ct)

                    val plaintext = noise.decrypt(ct)
                    if (plaintext == null) {
                        android.util.Log.e("SocketDataSource", "Decrypt failed for $host:$port — closing")
                        break
                    }

                    val stats = decodeSystemStats(plaintext) ?: continue
                    android.util.Log.d(
                        "SocketDataSource",
                        "Stats from $host:$port — CPU=${stats.cpuUsage}%, MEM=${stats.memUsage}%"
                    )
                    emit(stats)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SocketDataSource", "Socket error from $host:$port: ${e.message}")
                mutex.withLock { _connectedHosts.value = _connectedHosts.value - "$host:$port" }
            }
            kotlinx.coroutines.delay(1000L)
        }
    }
}
