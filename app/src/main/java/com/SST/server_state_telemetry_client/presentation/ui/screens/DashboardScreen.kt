package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import com.SST.server_state_telemetry_client.presentation.navigation.Screen
import com.SST.server_state_telemetry_client.presentation.ui.components.AddServerListItem
import com.SST.server_state_telemetry_client.presentation.ui.components.ServerItem
import com.SST.server_state_telemetry_client.presentation.viewmodel.MainViewModel
import com.SST.server_state_telemetry_client.ui.theme.Bad
import com.SST.server_state_telemetry_client.ui.theme.Bg
import com.SST.server_state_telemetry_client.ui.theme.Card as CardColor
import com.SST.server_state_telemetry_client.ui.theme.Ok
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import com.SST.server_state_telemetry_client.ui.theme.Text3

@Composable
fun DashboardScreen(navController: NavController, viewModel: MainViewModel = hiltViewModel()) {

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf(ServerDialogMode.ADD) }
    var editingServerId by remember { mutableStateOf<Int?>(null) }
    var formState by remember { mutableStateOf(ServerRegisterFormState()) }
    var formError by remember { mutableStateOf<String?>(null) }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val qrTextFlow =
        remember(savedStateHandle) { savedStateHandle?.getStateFlow<String?>("qr_text", "") }
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
        servers = servers,
        onServerClick = { server ->
            navController.navigate(Screen.ServerDetail.routeWithId(server.id))
        },
        onAddServerClick = { navController.navigate(Screen.QrScan.route) },
        onEditServerClick = { server ->
            editingServerId = server.id
            formState = ServerRegisterFormState(
                name = server.name,
                ip = server.ip,
                port = server.port.toString(),
                hmacKey = server.hmacKey
            )
            dialogMode = ServerDialogMode.EDIT
            formError = null
            showDialog = true
        },
        onDeleteServerClick = { server -> viewModel.deleteServer(server.id) }
    )

    if (showDialog) {
        ServerRegisterSheet(
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
                    return@ServerRegisterSheet
                }
                viewModel.connect(
                    formState.ip,
                    formState.port.toIntOrNull() ?: 443,
                    formState.hmacKey
                )
                if (dialogMode == ServerDialogMode.ADD) {
                    viewModel.addServer(
                        name = formState.name,
                        ip = formState.ip,
                        status = false,
                        port = formState.port.toIntOrNull() ?: 443,
                        hmacKey = formState.hmacKey
                    )
                } else {
                    val id = editingServerId
                    if (id == null) {
                        formError = "수정 대상 서버가 없습니다"
                        return@ServerRegisterSheet
                    }
                    viewModel.editServer(
                        id = id,
                        name = formState.name,
                        ip = formState.ip,
                        port = formState.port.toIntOrNull() ?: 443,
                        hmacKey = formState.hmacKey
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
    servers: List<RegistedServerList>?,
    onServerClick: (RegistedServerList) -> Unit,
    onAddServerClick: () -> Unit,
    onEditServerClick: (RegistedServerList) -> Unit,
    onDeleteServerClick: (RegistedServerList) -> Unit,
) {
    val safeServers = servers ?: emptyList()
    val totalCount = safeServers.size
    val onlineCount = safeServers.count { it.status }
    val offlineCount = totalCount - onlineCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Header
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            Text(
                text = "안녕하세요 👋",
                style = MaterialTheme.typography.labelLarge,
                color = Text3,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "내 서버",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = CardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                // Left: total server count
                Column {
                    Text(
                        text = "전체 서버",
                        style = MaterialTheme.typography.labelMedium,
                        color = Text3,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$totalCount",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "대",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Text3,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                    }
                }

                // Right: online / offline
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "온라인",
                            style = MaterialTheme.typography.labelMedium,
                            color = Text3,
                        )
                        Text(
                            text = "$onlineCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Ok,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "오프라인",
                            style = MaterialTheme.typography.labelMedium,
                            color = Text3,
                        )
                        Text(
                            text = "$offlineCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Bad,
                        )
                    }
                }
            }
        }

        // Section title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 0.dp)
                .padding(top = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "등록된 서버",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "최근 동기화 · 방금 전",
                style = MaterialTheme.typography.labelLarge,
                color = Text3,
            )
        }

        // Server list
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            items(items = safeServers, key = { it.id }) { server ->
                ServerItem(
                    server = server,
                    onEdit = { onEditServerClick(it) },
                    onDelete = { onDeleteServerClick(it) },
                    onClick = { onServerClick(server) }
                )
            }

            item(key = "add_server_item") {
                AddServerListItem(onClick = { onAddServerClick() })
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Dashboard - Servers")
@Composable
fun DashboardScreenPreview_LargeData() {
    val previewServers = remember {
        listOf(
            RegistedServerList(id = 1, ip = "192.168.1.100", name = "Production", status = true, port = 8443),
            RegistedServerList(id = 2, ip = "10.0.0.5", name = "Staging", status = true, port = 443),
            RegistedServerList(id = 3, ip = "172.16.0.10", name = "Dev Server", status = false, port = 9090),
            RegistedServerList(id = 4, ip = "192.168.1.200", name = "Monitoring", status = true, port = 443),
            RegistedServerList(id = 5, ip = "10.0.1.15", name = "Backup", status = false, port = 8443),
        )
    }

    Server_State_Telemetry_ClientTheme {
        DashboardContent(
            servers = previewServers,
            onServerClick = {},
            onDeleteServerClick = {},
            onEditServerClick = {},
            onAddServerClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Dashboard - Empty")
@Composable
fun DashboardScreenPreview_Empty() {
    Server_State_Telemetry_ClientTheme {
        DashboardContent(
            servers = emptyList(),
            onServerClick = {},
            onDeleteServerClick = {},
            onEditServerClick = {},
            onAddServerClick = {}
        )
    }
}
