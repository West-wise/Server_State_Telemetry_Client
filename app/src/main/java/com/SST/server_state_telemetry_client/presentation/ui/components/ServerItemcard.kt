package com.SST.server_state_telemetry_client.presentation.ui.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.domain.model.RegistedServerList
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SwipeAnchor{
    Center, Edit, Delete
}

@OptIn(ExperimentalFoundationApi::class)
private fun <T> AnchoredDraggableState<T>.safeOffset(): Float {
    return try {
        requireOffset()
    } catch (_: IllegalStateException) {
        0f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerItem(
    server: RegistedServerList?,     // null 방어
    onClick: (RegistedServerList) -> Unit,
    onEdit: (RegistedServerList) -> Unit,
    onDelete: (RegistedServerList) -> Unit,
    modifier: Modifier = Modifier
) {
    if (server == null) return
    val scope = rememberCoroutineScope()

    var itemWidthPx by remember { mutableStateOf(0) }
    val maxRevalPx = remember(itemWidthPx){
        if(itemWidthPx <= 0) 0f else itemWidthPx * 0.25f
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


    val safeName = server.name.ifBlank { "Unnamed" }
    val safeIp = server.ip.ifBlank { "0.0.0.0" }

    LaunchedEffect(maxRevalPx){
        if (maxRevalPx <= 0f) return@LaunchedEffect

        val anchors = DraggableAnchors {
            SwipeAnchor.Center at 0f
            SwipeAnchor.Edit at + maxRevalPx
            SwipeAnchor.Delete at - maxRevalPx
        }
        dragState.updateAnchors(anchors)
        dragState.snapTo(SwipeAnchor.Center)
    }

    Box (
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { itemWidthPx = it.width }
            .height(IntrinsicSize.Min)
    ) {
        val offsetX = dragState.safeOffset()
        val revealThreshold = maxRevalPx * 0.6f
        val editEnabled = offsetX >= revealThreshold
        val deleteEnabled = offsetX <= -revealThreshold
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                modifier = Modifier
                    .width(88.dp)
                    .fillMaxHeight(),
                onClick = {
                    onEdit(server)
                    scope.launch { dragState.animateTo(SwipeAnchor.Center) }
                },
                enabled = editEnabled,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit Server Info",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
            }
            Button(
                modifier = Modifier
                    .width(88.dp) // TODO(modified): 고정 폭
                    .fillMaxHeight(),
                onClick = {
                    onDelete(server)
                    scope.launch { dragState.animateTo(SwipeAnchor.Center) }
                },
                enabled = deleteEnabled,
                shape = RoundedCornerShape(
                    topEnd = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,      // 배경: 빨강
                    contentColor = MaterialTheme.colorScheme.onError      // 내용: 흰색(대비색)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete Server",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    val x = dragState.safeOffset().roundToInt()
                    IntOffset(x, 0)
                }
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Horizontal
                ),
            onClick = { onClick(server) }
        ) {
            Row (
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = safeName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = safeIp,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                StatusDot(server.status)
            }
        }
    }
}

@Composable
fun AddServerListItem(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }, // TODO(modified)
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center // TODO(modified): 가운데 정렬
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add Server"
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "서버 추가",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

