package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

enum class ServerDialogMode {
        ADD,
        EDIT
}

// TODO(modified): 폼 상태
data class ServerRegisterFormState(
        val name: String = "",
        val ip: String = "",
        val port: String = "",
        val hmacKey: String = ""
)

// TODO(modified): 등록 Dialog
@Composable
fun ServerRegisterDialog(
        mode: ServerDialogMode,
        state: ServerRegisterFormState?,
        errorText: String?,
        onStateChange: (ServerRegisterFormState) -> Unit,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
) {
        if (state == null) return // TODO(modified): null 방어

        var hmacVisible by remember { mutableStateOf(false) } // TODO(modified)

        AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text(if (mode == ServerDialogMode.ADD) "서버 등록" else "서버 수정") },
                text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!errorText.isNullOrBlank()) {
                                        Text(
                                                text = errorText,
                                                color = MaterialTheme.colorScheme.error
                                        )
                                }

                                OutlinedTextField(
                                        value = state.name,
                                        onValueChange = { onStateChange(state.copy(name = it)) },
                                        label = { Text("서버 별칭") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                )

                                OutlinedTextField(
                                        value = state.ip,
                                        onValueChange = { onStateChange(state.copy(ip = it)) },
                                        label = { Text("IP 주소") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                )

                                OutlinedTextField(
                                        value = state.port,
                                        onValueChange = { onStateChange(state.copy(port = it)) },
                                        label = { Text("포트") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                )

                                OutlinedTextField(
                                        value = state.hmacKey,
                                        onValueChange = { onStateChange(state.copy(hmacKey = it)) },
                                        label = { Text("HMAC Key") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation =
                                                if (hmacVisible) VisualTransformation.None
                                                else
                                                        PasswordVisualTransformation(), // TODO(modified)
                                        trailingIcon = {
                                                IconButton(
                                                        onClick = { hmacVisible = !hmacVisible }
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        if (hmacVisible)
                                                                                Icons.Filled
                                                                                        .VisibilityOff
                                                                        else
                                                                                Icons.Filled
                                                                                        .Visibility,
                                                                contentDescription =
                                                                        if (hmacVisible) "Hide HMAC"
                                                                        else "Show HMAC"
                                                        )
                                                }
                                        }
                                )
                        }
                },
                confirmButton = {
                        Button(onClick = { onConfirm() }) {
                                Text(if (mode == ServerDialogMode.ADD) "등록" else "수정")
                        }
                },
                dismissButton = { Button(onClick = { onDismiss() }) { Text("취소") } }
        )
}
