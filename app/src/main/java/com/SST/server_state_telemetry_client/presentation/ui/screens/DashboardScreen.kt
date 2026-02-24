package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import com.SST.server_state_telemetry_client.presentation.navigation.Screen
import com.SST.server_state_telemetry_client.presentation.ui.components.AddServerListItem
import com.SST.server_state_telemetry_client.presentation.ui.components.ServerItem
import com.SST.server_state_telemetry_client.presentation.ui.components.StatusDot
import com.SST.server_state_telemetry_client.presentation.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel()
) {

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    // 서버 추가/수정 관련 변수들
    var showDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf(ServerDialogMode.ADD)}
    var editingServerId by remember { mutableStateOf<Int?>(null) }
    var formState by remember { mutableStateOf(ServerRegisterFormState()) }
    var formError by remember { mutableStateOf<String?>(null) }

    // QR 관련 변수들(핸들, 읽어온 값 등등)
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val qrTextFlow = remember(savedStateHandle){
        savedStateHandle?.getStateFlow<String?>("qr_text", "")
    }
    val qrText by (qrTextFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") })

    LaunchedEffect(qrText) {
        if (!qrText.isNullOrBlank()) {

            savedStateHandle?.remove<String>("qr_text")

            val parsed = QrServerParser.parse(qrText)

            if (parsed != null) {
                formState = formState.copy(
                    name = parsed.name ?: "",
                    ip = parsed.ip ?: "",
                    port = parsed.port?.toString() ?: "",
                    hmacKey = parsed.hmacKey ?: ""
                )
                formError = null
                showDialog = true
            } else {
                formError = "QR 데이터가 올바르지 않습니다."
            }
        }
    }

    DashboardContent(
        title = "대시보드",
        connectionStatus = connectionStatus,
        servers = servers,
        onConnectClick = {_ ,_ ->},
        onServerClick = { server -> navController.navigate(Screen.ServerDetail.routeWithId(server.id)) },
        onAddServerClick = { navController.navigate(Screen.QrScan.route) },
        onEditServerClick = { server -> viewModel.editServer(server.id, server.name, server.ip) },
        onDeleteServerClick = { server -> viewModel.deleteServer(server.id) }
    )

    if (showDialog) {
        ServerRegisterDialog(
            mode = dialogMode,
            state = formState,
            errorText = formError,
            onStateChange = { formState = it },
            onDismiss = {
                showDialog = false
                formError = null
                editingServerId = null
            },
            onConfirm = {
                val err = FormValidator.validate(formState)
                if (err != null) {
                    formError = err
                    return@ServerRegisterDialog
                }
                if(dialogMode == ServerDialogMode.ADD){
                    viewModel.addServer(
                        name = formState.name,
                        ip = formState.ip,
                        status = false
                    )
                } else {
                    val id = editingServerId

                    if(id == null){
                        formError = "수정 대상 서버가 없습니다"
                        return@ServerRegisterDialog
                    }
                    viewModel.editServer(
                        id = id,
                        name = formState.name,
                        ip = formState.ip
                    )
                }

                showDialog = false
                formError = null
                editingServerId = null
            }
        )
    }
}


@Composable
fun DashboardContent(
    title: String,
    connectionStatus: String,
    servers: List<RegistedServerList>?,
    onServerClick: (RegistedServerList) -> Unit,
    onConnectClick: (String, Int) -> Unit,
    onAddServerClick: () -> Unit,
    onEditServerClick: (RegistedServerList) -> Unit,
    onDeleteServerClick: (RegistedServerList) -> Unit,
) {
    val safeServers = servers ?: emptyList()

    // 대시보드 카운터
    val totalCount = safeServers.size
    val onlineCount = safeServers.count { it.status }
    val offlineCount = totalCount - onlineCount

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(25.dp)) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            ServerCountSummary(
                total = totalCount,
                online = onlineCount,
                offline = offlineCount
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Registered Servers",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // TODO(modified): 리스트가 남은 공간을 가져가도록 weight(1f)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 서버 목록
                items(
                    items = safeServers,
                    key = { it.id }
                ) { server ->
                    ServerItem(
                        server = server,
                        onEdit = { onEditServerClick(it) },
                        onDelete = { onDeleteServerClick(it) },
                        onClick = { onServerClick(server) }
                    )
                }

                // TODO(modified): 리스트 마지막에 + 추가 아이템
                item(key = "add_server_item") {
                    AddServerListItem(
                        onClick = { onAddServerClick() }
                    )
                }
            }
        }
    }
}


@Composable
private fun ServerCountSummary(
    total: Int,
    online: Int,
    offline: Int
) {
    // 값 방어 (의도치 않은 음수 등)
    val safeTotal = if (total < 0) 0 else total // TODO(modified)
    val safeOnline = if (online < 0) 0 else online // TODO(modified)
    val safeOffline = if (offline < 0) 0 else offline // TODO(modified)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total: $safeTotal",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(isOnline = true) // TODO(modified)
                Spacer(modifier = Modifier.size(6.dp)) // TODO(modified)
                Text(text = "Online: $safeOnline", style = MaterialTheme.typography.bodyMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(isOnline = false)
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = "Offline: $safeOffline", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true) // TODO(modified)
@Composable
fun DashboardScreenPreview_LargeData() {

    // TODO(modified): 20개 더미 서버 생성 (ID 중복 방지)
    val previewServers = remember {
        List(7) { index ->
            RegistedServerList(
                id = index + 1,
                ip = "192.168.0.${index + 10}",
                name = "Server-${index + 1}",
                status = index % 2 == 0 // ONLINE / OFFLINE 교차
            )
        }
    }

    com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme {

        DashboardContent(
            title = "Dashboard (Preview)", // TODO(modified)
            connectionStatus = "Connected to 192.168.1.100:5000",
            servers = previewServers,
            onConnectClick = { host, port ->
                if (host.isBlank() || port !in 1..65535) return@DashboardContent
            },
            onServerClick = { server ->
                if (server.ip.isBlank()) return@DashboardContent
            },
            onDeleteServerClick = {},
            onEditServerClick = {},
            onAddServerClick = {}
        )
    }
}
