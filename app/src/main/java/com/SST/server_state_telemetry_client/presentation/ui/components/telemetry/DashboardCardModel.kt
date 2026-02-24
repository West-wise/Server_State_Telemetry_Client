package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import com.SST.server_state_telemetry_client.domain.model.DiskSummary
import com.SST.server_state_telemetry_client.domain.model.SystemStats
import com.SST.server_state_telemetry_client.presentation.ui.screens.DetailType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CardKind {
    SEVEN_SEG_PERCENT,
    NET_GRAPH_RX,
    NET_GRAPH_TX,
    PROCESS_RATIO,
    FD_RATIO,
    UPTIME,
    USERS,
    PARTITIONS_ROOT
}

private data class DashboardCard(
    val id: String,
    val title: String,
    val kind: CardKind,
    val clickable: Boolean,
    val detailType: DetailType?,
    val primaryInt: Int? = null,
    val secondaryInt: Int? = null,
    val primaryLong: Long? = null,
    val secondaryLong: Long? = null
)

private fun buildDashboardCards(stats: SystemStats?): List<DashboardCard> {
    val s = stats
    return listOf(
        DashboardCard(
            id = "cpu", title = "CPU",
            kind = CardKind.SEVEN_SEG_PERCENT,
            clickable = false, detailType = null,
            primaryInt = s?.cpuUsage ?: 0
        ),
        DashboardCard(
            id = "ram", title = "RAM",
            kind = CardKind.SEVEN_SEG_PERCENT,
            clickable = false, detailType = null,
            primaryInt = s?.memUsage ?: 0
        ),
        DashboardCard(
            id = "net_rx", title = "Download",
            kind = CardKind.NET_GRAPH_RX,
            clickable = true, detailType = DetailType.NETWORK_RX,
            primaryLong = s?.netRx?.bytePerSec ?: 0L
        ),
        DashboardCard(
            id = "net_tx", title = "Upload",
            kind = CardKind.NET_GRAPH_TX,
            clickable = true, detailType = DetailType.NETWORK_TX,
            primaryLong = s?.netTx?.bytePerSec ?: 0L
        ),
        DashboardCard(
            id = "proc", title = "Processes",
            kind = CardKind.PROCESS_RATIO,
            clickable = false, detailType = null,
            primaryLong = s?.procCount ?: 0L,
            secondaryLong = s?.totalProcCount ?: 0L
        ),
        DashboardCard(
            id = "fd", title = "File Descriptors",
            kind = CardKind.FD_RATIO,
            clickable = false, detailType = null,
            primaryLong = s?.fdInfo?.usingFdCnt ?: 0L,
            secondaryLong = s?.fdInfo?.allocatedFdCnt ?: 0L
        ),
        DashboardCard(
            id = "uptime", title = "Uptime",
            kind = CardKind.UPTIME,
            clickable = false, detailType = null,
            primaryLong = s?.uptimeSecs ?: 0L
        ),
        DashboardCard(
            id = "users", title = "Users",
            kind = CardKind.USERS,
            clickable = true, detailType = DetailType.USERS,
            primaryInt = s?.connectedUserCount ?: 0,
            secondaryInt = s?.netUserCount ?: 0
        ),
        DashboardCard(
            id = "disk", title = "Partitions (/)",
            kind = CardKind.PARTITIONS_ROOT,
            clickable = true, detailType = DetailType.PARTITIONS,
            primaryInt = rootUsagePercent(s?.disk)
        )
    )
}

private fun rootUsagePercent(d: DiskSummary?): Int {
    val disk = d ?: return 0
    val total = max(1L, disk.totalRoot)
    val used = min(max(0L, disk.usedRoot), total)
    return ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
}