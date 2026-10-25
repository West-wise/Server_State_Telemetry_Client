package com.SST.server_state_telemetry_client.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateDownloadClientTest {
    @Test(timeout = 35_000L)
    fun productionDefaultsAllowStreamingPastTheOldCioFifteenSecondLimit() = runBlocking {
        LoopbackResponse(3) { output, _ ->
            output.write('a'.code)
            output.flush()
            Thread.sleep(8_000L)
            output.write('b'.code)
            output.flush()
            Thread.sleep(8_000L)
            output.write('c'.code)
            output.flush()
        }.use { server ->
            // Use the real production factory and streaming API, not an engine fixture.
            createAppUpdateDownloadClient().use { client ->
                assertArrayEquals("abc".toByteArray(), readStreamingBody(client, server.url))
            }
        }
    }

    @Test(timeout = 12_000L)
    fun requestDeadlineStopsATransferEvenWhileBodyBytesKeepArriving() = runBlocking {
        val bytesSent = AtomicInteger()
        LoopbackResponse(30) { output, stopped ->
            repeat(30) {
                if (stopped.await(200L, TimeUnit.MILLISECONDS)) return@LoopbackResponse
                output.write('a'.code)
                output.flush()
                bytesSent.incrementAndGet()
            }
        }.use { server ->
            createAppUpdateDownloadClient(
                requestTimeoutMillis = 2_000L,
                connectTimeoutMillis = 1_500L,
                socketTimeoutMillis = 5_000L
            ).use { client ->
                val failure = runCatching { readStreamingBody(client, server.url) }.exceptionOrNull()
                assertHasCause(failure, HttpRequestTimeoutException::class.java)
                assertTrue("The deadline must expire during an active body transfer", bytesSent.get() >= 2)
            }
        }
    }

    @Test(timeout = 12_000L)
    fun socketInactivityStopsAStalledBodyBeforeTheRequestDeadline() = runBlocking {
        val bytesSent = AtomicInteger()
        LoopbackResponse(2) { output, stopped ->
            output.write('a'.code)
            output.flush()
            bytesSent.incrementAndGet()
            // Keep the response open, without sending the second promised byte.
            stopped.await(10L, TimeUnit.SECONDS)
        }.use { server ->
            createAppUpdateDownloadClient(
                requestTimeoutMillis = 5_000L,
                connectTimeoutMillis = 1_500L,
                socketTimeoutMillis = 1_000L
            ).use { client ->
                val failure = runCatching { readStreamingBody(client, server.url) }.exceptionOrNull()
                assertHasCause(failure, SocketTimeoutException::class.java)
                assertEquals("Headers and a body byte must arrive before the stall", 1, bytesSent.get())
            }
        }
    }

    private suspend fun readStreamingBody(client: HttpClient, url: String): ByteArray =
        client.prepareGet(url).execute { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val packet = response.bodyAsChannel().readRemaining()
            try { packet.readBytes() } finally { packet.close() }
        }

    private fun assertHasCause(failure: Throwable?, expected: Class<out Throwable>) {
        assertNotNull("The transfer unexpectedly completed", failure)
        assertTrue(
            "Expected ${expected.simpleName}, received ${failure?.javaClass?.name}",
            generateSequence(requireNotNull(failure)) { it.cause }.any { expected.isInstance(it) }
        )
    }

    private class LoopbackResponse(
        bodyLength: Int,
        body: (OutputStream, CountDownLatch) -> Unit
    ) : AutoCloseable {
        private val stopped = CountDownLatch(1)
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val connection = AtomicReference<Socket?>()
        val url = "http://127.0.0.1:${listener.localPort}/update.apk"
        private val worker = thread(isDaemon = true, name = "sstc-update-timeout-test") {
            try {
                listener.accept().use { socket ->
                    connection.set(socket)
                    if (stopped.count == 0L) return@use
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (true) {
                        val line = reader.readLine() ?: throw IOException("Client closed before request headers")
                        if (line.isEmpty()) break
                    }
                    val output = socket.getOutputStream()
                    output.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                            "Content-Length: $bodyLength\r\nConnection: close\r\n\r\n")
                            .toByteArray(Charsets.US_ASCII)
                    )
                    output.flush()
                    body(output, stopped)
                }
            } catch (_: IOException) {
                // The timeout tests intentionally close the connection before the body is complete.
            } catch (_: InterruptedException) {
                // Cleanup also interrupts sleeps, so a failed assertion cannot retain this worker.
                Thread.currentThread().interrupt()
            }
        }

        override fun close() {
            stopped.countDown()
            listener.close()
            connection.get()?.close()
            worker.interrupt()
            worker.join(2_000L)
            assertFalse("Loopback server worker did not stop", worker.isAlive)
        }
    }
}
