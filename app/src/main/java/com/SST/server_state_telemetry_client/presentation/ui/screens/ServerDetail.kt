package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.domain.model.DiskSummary
import com.SST.server_state_telemetry_client.domain.model.SystemStats
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.MetricCard
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.NetworkGraphCard
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.RatioText
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.SevenSegPercentText
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.UptimeText
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.UsersSummaryCard
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.pushHistory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DetailType {
    NETWORK_RX,
    NETWORK_TX,
    USERS,
    PARTITIONS
}

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
                    id = "cpu",
                    title = "CPU",
                    kind = CardKind.SEVEN_SEG_PERCENT,
                    clickable = false,
                    detailType = null,
                    primaryInt = s?.cpuUsage ?: 0
            ),
            DashboardCard(
                    id = "ram",
                    title = "RAM",
                    kind = CardKind.SEVEN_SEG_PERCENT,
                    clickable = false,
                    detailType = null,
                    primaryInt = s?.memUsage ?: 0
            ),
            DashboardCard(
                    id = "net_rx",
                    title = "Download",
                    kind = CardKind.NET_GRAPH_RX,
                    clickable = true,
                    detailType = DetailType.NETWORK_RX,
                    primaryLong = s?.netRx?.bytePerSec ?: 0L
            ),
            DashboardCard(
                    id = "net_tx",
                    title = "Upload",
                    kind = CardKind.NET_GRAPH_TX,
                    clickable = true,
                    detailType = DetailType.NETWORK_TX,
                    primaryLong = s?.netTx?.bytePerSec ?: 0L
            ),
            DashboardCard(
                    id = "proc",
                    title = "Processes",
                    kind = CardKind.PROCESS_RATIO,
                    clickable = false,
                    detailType = null,
                    primaryLong = s?.procCount ?: 0L,
                    secondaryLong = s?.totalProcCount ?: 0L
            ),
            DashboardCard(
                    id = "fd",
                    title = "File Descriptors",
                    kind = CardKind.FD_RATIO,
                    clickable = false,
                    detailType = null,
                    primaryLong = s?.fdInfo?.usingFdCnt ?: 0L,
                    secondaryLong = s?.fdInfo?.allocatedFdCnt ?: 0L
            ),
            DashboardCard(
                    id = "uptime",
                    title = "Uptime",
                    kind = CardKind.UPTIME,
                    clickable = false,
                    detailType = null,
                    primaryLong = s?.uptimeSecs ?: 0L
            ),
            DashboardCard(
                    id = "users",
                    title = "Users",
                    kind = CardKind.USERS,
                    clickable = true,
                    detailType = DetailType.USERS,
                    primaryInt = s?.connectedUserCount ?: 0,
                    secondaryInt = s?.netUserCount ?: 0
            ),
            DashboardCard(
                    id = "disk",
                    title = "Partitions (/)",
                    kind = CardKind.PARTITIONS_ROOT,
                    clickable = true,
                    detailType = DetailType.PARTITIONS,
                    primaryInt = rootUsagePercent(s?.disk)
            )
    )
}

fun rootUsagePercent(d: DiskSummary?): Int {
    val disk = d ?: return 0
    val total = max(1L, disk.totalRoot)
    val used = min(max(0L, disk.usedRoot), total)
    return ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
}

@Composable
fun TelemetryDashboardScreen(
        stats: SystemStats?, // null 방어
) {
    var detail by remember { mutableStateOf<DetailType?>(null) } // TODO(modified)

    // TODO(modified): 네트워크 그래프용 히스토리(최근 N초)
    val rxHistory = remember { mutableStateListOf<Long>() }
    val txHistory = remember { mutableStateListOf<Long>() }

    LaunchedEffect(stats?.netRx?.bytePerSec, stats?.netTx?.bytePerSec) {
        // null이면 업데이트 하지 않음
        val rx = stats?.netRx?.bytePerSec
        val tx = stats?.netTx?.bytePerSec
        if (rx != null) pushHistory(rxHistory, rx)
        if (tx != null) pushHistory(txHistory, tx)
    }

    val cards = remember(stats) { buildDashboardCards(stats) }

    Box(Modifier.fillMaxSize().padding(12.dp)) {
        LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
        ) {
            items(cards, key = { it.id }) { card ->
                MetricCard(
                        title = card.title,
                        clickable = card.clickable,
                        onClick = {
                            if (!card.clickable) return@MetricCard
                            detail = card.detailType
                        }
                ) {
                    when (card.kind) {
                        CardKind.SEVEN_SEG_PERCENT -> SevenSegPercentText(card.primaryInt ?: 0)
                        CardKind.PROCESS_RATIO ->
                                RatioText(
                                        left = card.primaryLong ?: 0L,
                                        right = card.secondaryLong ?: 0L,
                                        suffix = ""
                                )
                        CardKind.FD_RATIO ->
                                RatioText(
                                        left = card.primaryLong ?: 0L,
                                        right = card.secondaryLong ?: 0L,
                                        suffix = ""
                                )
                        CardKind.UPTIME -> UptimeText(seconds = card.primaryLong ?: 0L)
                        CardKind.NET_GRAPH_RX ->
                                NetworkGraphCard(
                                        currentBps = card.primaryLong ?: 0L,
                                        history = rxHistory,
                                        label = "DL"
                                )
                        CardKind.NET_GRAPH_TX ->
                                NetworkGraphCard(
                                        currentBps = card.primaryLong ?: 0L,
                                        history = txHistory,
                                        label = "UL"
                                )
                        CardKind.USERS ->
                                UsersSummaryCard(
                                        connected = card.primaryInt ?: 0,
                                        networkUsers = card.secondaryInt ?: 0
                                )
                        CardKind.PARTITIONS_ROOT -> SevenSegPercentText(card.primaryInt ?: 0)
                    }
                }
            }
        }

        // ===== 상세 Dialog =====
        //        when (detail) {
        //            DetailType.NETWORK_RX -> NetworkDetailDialog(
        //                title = "Network Download",
        //                history = rxHistory,
        //                onDismiss = { detail = null }
        //            )
        //            DetailType.NETWORK_TX -> NetworkDetailDialog(
        //                title = "Network Upload",
        //                history = txHistory,
        //                onDismiss = { detail = null }
        //            )
        //            DetailType.USERS -> UsersDetailDialog(
        //                connected = stats?.connectedUserCount ?: 0,
        //                networkUsers = stats?.netUserCount ?: 0,
        //                onDismiss = { detail = null }
        //            )
        //            DetailType.PARTITIONS -> PartitionsDetailDialog(
        //                disk = stats?.disk,
        //                onDismiss = { detail = null }
        //            )
        //            null -> Unit
        //        }
    }
}

@Preview(showBackground = true, name = "Telemetry Dashboard Empty Data")
@Composable
fun TelemetryDashboardScreenPreview() {
    MaterialTheme { Surface { TelemetryDashboardScreen(stats = null) } }
}
