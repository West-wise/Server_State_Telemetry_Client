package com.SST.server_state_telemetry_client.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout

internal const val APP_UPDATE_DOWNLOAD_TIMEOUT_MILLIS = 180_000L

internal fun createAppUpdateDownloadClient(
    requestTimeoutMillis: Long = APP_UPDATE_DOWNLOAD_TIMEOUT_MILLIS,
    connectTimeoutMillis: Long = 10_000L,
    socketTimeoutMillis: Long = 60_000L
): HttpClient {
    val requestLimit = requestTimeoutMillis
    val connectLimit = connectTimeoutMillis
    val socketLimit = socketTimeoutMillis
    return HttpClient(CIO) {
        install(HttpTimeout) {
            this.requestTimeoutMillis = requestLimit
            this.connectTimeoutMillis = connectLimit
            this.socketTimeoutMillis = socketLimit
        }
    }
}
