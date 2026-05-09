package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme


@Composable
fun UsersSummaryCard(connected: Int, networkUsers: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Filled.People, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text = "$connected", style = MaterialTheme.typography.headlineSmall)
    }
    Spacer(Modifier.height(6.dp))
    Text(text = "Net Users: $networkUsers", style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun UsersDetailDialog(connected: Int, networkUsers: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connected Users") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connected users: $connected")
                Text("Network users: $networkUsers")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("닫기") } }
    )
}

@Preview(showBackground = true)
@Composable
private fun UsersSummaryCardPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                UsersSummaryCard(connected = 5, networkUsers = 12)
            }
        }
    }
}
