package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.SST.server_state_telemetry_client.domain.model.DiskSummary
import com.SST.server_state_telemetry_client.domain.model.SystemStats
import com.SST.server_state_telemetry_client.presentation.ui.components.StatusPill
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.MetricRing
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.NetworkGraphCard
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.PartitionsDetailDialog
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.UptimeText
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.UsersSummaryCard
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.UsersDetailDialog
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.RatioText
import com.SST.server_state_telemetry_client.presentation.ui.components.telemetry.pushHistory
import com.SST.server_state_telemetry_client.ui.theme.Bg
import com.SST.server_state_telemetry_client.ui.theme.Card as CardColor
import com.SST.server_state_telemetry_client.ui.theme.Divider
import com.SST.server_state_telemetry_client.ui.theme.Primary
import com.SST.server_state_telemetry_client.ui.theme.Text3
import com.SST.server_state_telemetry_client.ui.theme.Text4
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DetailType {
    NETWORK_RX, NETWORK_TX, USERS, PARTITIONS
}

fun rootUsagePercent(d: DiskSummary?): Int {
    val disk = d ?: return 0
    val total = max(1L, disk.totalRoot)
    val used = min(max(0L, disk.usedRoot), total)
    return ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
}

@Composable
fun ServerDetailScreen(
    serverId: Int,
    onBack: () -> Unit,
    viewModel: com.SST.server_state_telemetry_client.presentation.viewmodel.MainViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server = servers.find { it.id == serverId }

    LaunchedEffect(server) {
        if (server != null && server.ip.isNotBlank()) {
            viewModel.connect(server.ip, server.port, server.hmacKey)
        }
    }

    val statsFlow = remember(server) {
        if (server != null && server.ip.isNotBlank()) {
            viewModel.getServerState(server.ip, server.port)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }

    val stats by statsFlow.collectAsStateWithLifecycle(initialValue = null)
    var detail by remember { mutableStateOf<DetailType?>(null) }

    val rxHistory = remember { mutableStateListOf<Long>() }
    val txHistory = remember { mutableStateListOf<Long>() }

    LaunchedEffect(stats?.netRx?.bytePerSec, stats?.netTx?.bytePerSec) {
        val rx = stats?.netRx?.bytePerSec
        val tx = stats?.netTx?.bytePerSec
        if (rx != null) pushHistory(rxHistory, rx)
        if (tx != null) pushHistory(txHistory, tx)
    }

    // Detail dialogs
    when (detail) {
        DetailType.USERS -> UsersDetailDialog(
            connected = stats?.connectedUserCount ?: 0,
            networkUsers = stats?.netUserCount ?: 0,
            onDismiss = { detail = null }
        )
        DetailType.PARTITIONS -> PartitionsDetailDialog(
            disk = stats?.disk,
            onDismiss = { detail = null }
        )
        else -> {}
    }

    val cpu = stats?.cpuUsage ?: 0
    val ram = stats?.memUsage ?: 0
    val diskPercent = rootUsagePercent(stats?.disk)
    val isOnline = server?.status == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp
        )
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    modifier = Modifier.size(36.dp),
                    onClick = { onBack() },
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("‹", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Text(
                    text = server?.name ?: "서버",
                    style = MaterialTheme.typography.titleMedium,
                )

                Box(Modifier.size(36.dp))
            }
        }

        // Hero Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            StatusPill(online = isOnline)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "${server?.ip ?: "0.0.0.0"} · :${server?.port ?: 443}",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                color = Text3,
                            )
                            Spacer(Modifier.height(2.dp))
                            val uptimeSecs = stats?.uptimeSecs ?: 0L
                            val d = uptimeSecs / 86400
                            val h = (uptimeSecs % 86400) / 3600
                            val m = (uptimeSecs % 3600) / 60
                            val sec = uptimeSecs % 60
                            Text(
                                text = "업타임 ${d}d %02d:%02d:%02d".format(h, m, sec),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                color = Text3,
                            )
                        }

                        MetricRing(percent = cpu, size = 88.dp, stroke = 10.dp)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 16.dp),
                        thickness = 1.dp,
                        color = Divider,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatColumn("CPU", "$cpu%")
                        VerticalDivider()
                        StatColumn("메모리", "$ram%")
                        VerticalDivider()
                        StatColumn("디스크", "$diskPercent%")
                    }
                }
            }
        }

        // Network Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "네트워크",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Text3,
                        )
                        Text(
                            text = "최근 60초",
                            style = MaterialTheme.typography.labelSmall,
                            color = Text4,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Download
                    NetworkGraphCard(
                        currentBps = stats?.netRx?.bytePerSec ?: 0L,
                        history = rxHistory,
                        label = "↓ 다운로드",
                        color = Primary,
                    )

                    Spacer(Modifier.height(10.dp))

                    // Upload
                    NetworkGraphCard(
                        currentBps = stats?.netTx?.bytePerSec ?: 0L,
                        history = txHistory,
                        label = "↑ 업로드",
                        color = Color(0xFF9B6CDF),
                    )
                }
            }
        }

        // Tile grid - using individual items since we're in a LazyColumn
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TileCard(
                    modifier = Modifier.weight(1f),
                    label = "프로세스",
                    value = "${stats?.procCount ?: 0}",
                    sub = "/ ${stats?.totalProcCount ?: 0}",
                )
                TileCard(
                    modifier = Modifier.weight(1f),
                    label = "파일 디스크립터",
                    value = "${stats?.fdInfo?.usingFdCnt ?: 0}",
                    sub = "/ ${stats?.fdInfo?.allocatedFdCnt ?: 0}",
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TileCard(
                    modifier = Modifier.weight(1f).clickable { detail = DetailType.USERS },
                    label = "접속 유저",
                    value = "${stats?.connectedUserCount ?: 0}",
                    sub = "명",
                )
                // Root partition tile with MetricRing
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(118.dp)
                        .clickable { detail = DetailType.PARTITIONS },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "루트 파티션",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Text3,
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            MetricRing(percent = diskPercent, size = 48.dp, stroke = 6.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Text3,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(Divider)
    )
}

@Composable
private fun TileCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String,
) {
    Card(
        modifier = modifier.height(118.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Text3,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = " $sub",
                    style = MaterialTheme.typography.labelMedium,
                    color = Text3,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Server Detail Empty")
@Composable
fun ServerDetailScreenPreview() {
    MaterialTheme { ServerDetailScreen(serverId = -1, onBack = {}) }
}
