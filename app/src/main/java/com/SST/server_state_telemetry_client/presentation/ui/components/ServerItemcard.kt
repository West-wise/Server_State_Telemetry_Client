package com.SST.server_state_telemetry_client.presentation.ui.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import com.SST.server_state_telemetry_client.ui.theme.Bad
import com.SST.server_state_telemetry_client.ui.theme.BadSoft
import com.SST.server_state_telemetry_client.ui.theme.Card as CardColor
import com.SST.server_state_telemetry_client.ui.theme.Divider
import com.SST.server_state_telemetry_client.ui.theme.Primary
import com.SST.server_state_telemetry_client.ui.theme.PrimarySoft
import com.SST.server_state_telemetry_client.ui.theme.Text3
import com.SST.server_state_telemetry_client.ui.theme.Text4
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SwipeAnchor {
    Center, Edit, Delete
}

@OptIn(ExperimentalFoundationApi::class)
private fun <T> AnchoredDraggableState<T>.safeOffset(): Float {
    return try { requireOffset() } catch (_: IllegalStateException) { 0f }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerItem(
    server: RegistedServerList?,
    onClick: (RegistedServerList) -> Unit,
    onEdit: (RegistedServerList) -> Unit,
    onDelete: (RegistedServerList) -> Unit,
    modifier: Modifier = Modifier
) {
    if (server == null) return
    val scope = rememberCoroutineScope()

    var itemWidthPx by remember { mutableStateOf(0) }
    val maxRevalPx = remember(itemWidthPx) {
        if (itemWidthPx <= 0) 0f else itemWidthPx * 0.25f
    }

    val dragState = remember {
        AnchoredDraggableState(
            initialValue = SwipeAnchor.Center,
            positionalThreshold = { distance -> distance * 0.33f },
            velocityThreshold = { 1200f },
            snapAnimationSpec = tween(durationMillis = 180),
            decayAnimationSpec = exponentialDecay()
        )
    }

    LaunchedEffect(maxRevalPx) {
        if (maxRevalPx <= 0f) return@LaunchedEffect
        val anchors = DraggableAnchors {
            SwipeAnchor.Center at 0f
            SwipeAnchor.Edit at +maxRevalPx
            SwipeAnchor.Delete at -maxRevalPx
        }
        dragState.updateAnchors(anchors)
        dragState.snapTo(SwipeAnchor.Center)
    }

    val safeName = server.name.ifBlank { "Unnamed" }
    val safeIp = server.ip.ifBlank { "0.0.0.0" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { itemWidthPx = it.width }
            .height(IntrinsicSize.Min)
    ) {
        val offsetX = dragState.safeOffset()
        val revealThreshold = maxRevalPx * 0.6f
        val editEnabled = offsetX >= revealThreshold
        val deleteEnabled = offsetX <= -revealThreshold

        // Revealed buttons behind the card
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.width(88.dp).fillMaxHeight(),
                onClick = {
                    onEdit(server)
                    scope.launch { dragState.animateTo(SwipeAnchor.Center) }
                },
                enabled = editEnabled,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimarySoft,
                    contentColor = Primary
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Button(
                modifier = Modifier.width(88.dp).fillMaxHeight(),
                onClick = {
                    onDelete(server)
                    scope.launch { dragState.animateTo(SwipeAnchor.Center) }
                },
                enabled = deleteEnabled,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BadSoft,
                    contentColor = Bad
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(ButtonDefaults.IconSize))
            }
        }

        // Foreground card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragState.safeOffset().roundToInt(), 0) }
                .anchoredDraggable(state = dragState, orientation = Orientation.Horizontal),
            onClick = { onClick(server) },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = CardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Avatar
                val avatarBg = if (server.status) PrimarySoft else Divider
                val avatarColor = if (server.status) Primary else Text4
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(avatarBg, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = safeName.first().uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = avatarColor,
                    )
                }

                // Middle: name + ip
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = safeName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "$safeIp · :${server.port}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = Text3,
                    )
                }

                // Right: status pill
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill(online = server.status)
                }
            }
        }
    }
}

@Composable
fun AddServerListItem(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(PrimarySoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = Primary,
                )
            }
            Text(
                text = "QR로 새 서버 추가하기",
                style = MaterialTheme.typography.titleMedium,
                color = Primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerItemOnlinePreview() {
    Server_State_Telemetry_ClientTheme {
        Column(
            modifier = Modifier.padding(16.dp).background(com.SST.server_state_telemetry_client.ui.theme.Bg),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ServerItem(
                server = RegistedServerList(
                    id = 1, ip = "192.168.1.100", name = "Production",
                    status = true, port = 8443, hmacKey = "abc123"
                ),
                onClick = {}, onEdit = {}, onDelete = {}
            )
            ServerItem(
                server = RegistedServerList(
                    id = 2, ip = "10.0.0.5", name = "Staging",
                    status = false, port = 443, hmacKey = "def456"
                ),
                onClick = {}, onEdit = {}, onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddServerListItemPreview() {
    Server_State_Telemetry_ClientTheme {
        Box(Modifier.padding(16.dp)) {
            AddServerListItem(onClick = {})
        }
    }
}
