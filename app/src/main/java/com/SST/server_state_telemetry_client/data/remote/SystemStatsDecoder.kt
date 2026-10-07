package com.SST.server_state_telemetry_client.data.remote

import com.SST.server_state_telemetry_client.domain.model.DiskSummary
import com.SST.server_state_telemetry_client.domain.model.FdInfo
import com.SST.server_state_telemetry_client.domain.model.NetInfo
import com.SST.server_state_telemetry_client.domain.model.SystemStats
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decode the existing SSTD packed 24-byte header and 134-byte statistics body. */
internal fun decodeSystemStats(plaintext: ByteArray): SystemStats? {
    if (plaintext.size < 24) return null
    val header = ByteBuffer.wrap(plaintext).order(ByteOrder.LITTLE_ENDIAN)
    if (header.int != 0x53535444) return null
    header.get() // version: preserve the existing receiver's behavior
    val type = header.get()
    header.short // clientId
    header.int // requestId
    header.long // timestamp: preserve the existing receiver's behavior
    val bodyLen = header.int
    if (type != 0x11.toByte() || bodyLen != 134 || plaintext.size < 158) return null

    val body = ByteBuffer.wrap(plaintext, 24, 134).order(ByteOrder.LITTLE_ENDIAN)
    val validMask = body.short.toInt() and 0xFFFF
    body.short // reserved
    val cpuUsage = body.get().toInt() and 0xFF
    val memUsage = body.get().toInt() and 0xFF
    val netRx = NetInfo(
        body.long, body.int.toLong() and 0xFFFFFFFFL,
        body.int.toLong() and 0xFFFFFFFFL, body.int.toLong() and 0xFFFFFFFFL
    )
    val netTx = NetInfo(
        body.long, body.int.toLong() and 0xFFFFFFFFL,
        body.int.toLong() and 0xFFFFFFFFL, body.int.toLong() and 0xFFFFFFFFL
    )
    val procCount = body.int.toLong() and 0xFFFFFFFFL
    val totalProcCount = body.int.toLong() and 0xFFFFFFFFL
    val netUserCount = body.short.toInt() and 0xFFFF
    val connectedUserCount = body.short.toInt() and 0xFFFF
    val uptimeSecs = body.int.toLong() and 0xFFFFFFFFL
    val fdInfo = FdInfo(body.int.toLong() and 0xFFFFFFFFL, body.int.toLong() and 0xFFFFFFFFL)
    val disk = DiskSummary(
        body.long, body.long, body.long, body.long,
        body.long, body.long, body.long, body.long
    )
    return SystemStats(
        validMask, cpuUsage, memUsage, netRx, netTx, procCount, totalProcCount,
        netUserCount, connectedUserCount, uptimeSecs, fdInfo, disk
    )
}
