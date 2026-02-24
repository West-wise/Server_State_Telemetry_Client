package com.SST.server_state_telemetry_client.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerState(
    val cpuUsage: Float,
    val memoryUsage: Float,
    val diskUsage: Float,
    val temperature: Float,
    val timestamp: Long
)

@Serializable
data class NetInfo(
    val bytePerSec: Long,
    val packetPerSec: Long,
    val errPerSec: Long,
    val dropPerSec: Long
)

@Serializable
data class FdInfo(
    val allocatedFdCnt: Long,
    val usingFdCnt: Long
)

@Serializable
data class DiskSummary(
    val totalRoot: Long, val usedRoot: Long,
    val totalHome: Long, val usedHome: Long,
    val totalVar: Long,  val usedVar: Long,
    val totalBoot: Long, val usedBoot: Long
)

@Serializable
data class SystemStats(
    val validMask: Int,
    val cpuUsage: Int,
    val memUsage: Int,
    val netRx: NetInfo,
    val netTx: NetInfo,
    val procCount: Long,
    val totalProcCount: Long,
    val netUserCount: Int,
    val connectedUserCount: Int,
    val uptimeSecs: Long,
    val fdInfo: FdInfo,
    val disk: DiskSummary
)