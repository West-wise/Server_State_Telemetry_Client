package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.ui.theme.Bad
import com.SST.server_state_telemetry_client.ui.theme.Bg
import com.SST.server_state_telemetry_client.ui.theme.Card
import com.SST.server_state_telemetry_client.ui.theme.Primary
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import com.SST.server_state_telemetry_client.ui.theme.Text1
import com.SST.server_state_telemetry_client.ui.theme.Text3

enum class ServerDialogMode {
    ADD, EDIT
}

data class ServerRegisterFormState(
    val name: String = "",
    val ip: String = "",
    val port: String = "",
    val pubKey: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerRegisterSheet(
    mode: ServerDialogMode,
    state: ServerRegisterFormState,
    errorText: String?,
    onStateChange: (ServerRegisterFormState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Card,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                if (mode == ServerDialogMode.ADD) "서버 등록" else "서버 수정",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "QR에서 읽어온 정보를 확인해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = Text3,
            )
            Spacer(Modifier.height(18.dp))

            errorText?.let {
                Text(
                    it, color = Bad,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            FilledField("서버 별칭", state.name) { onStateChange(state.copy(name = it)) }
            FilledField("IP 주소", state.ip, mono = true) { onStateChange(state.copy(ip = it)) }
            FilledField("포트", state.port, mono = true, keyboard = KeyboardType.Number) {
                onStateChange(state.copy(port = it))
            }
            FilledField("서버 공개키", state.pubKey, mono = true, password = true) {
                onStateChange(state.copy(pubKey = it))
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(
                    if (mode == ServerDialogMode.ADD) "등록하기" else "수정하기",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text("취소", color = Text3, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FilledField(
    label: String,
    value: String,
    mono: Boolean = false,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Bg, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Text3)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Text1,
                    fontFamily = if (mono) FontFamily.Monospace else null,
                ),
                modifier = Modifier.weight(1f),
            )
            if (password) {
                IconButton(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "숨기기" else "보기",
                        tint = Text3,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Server Register Form (ADD)")
@Composable
private fun ServerRegisterFormAddPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface(color = Card) {
            Column(
                Modifier
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp, top = 16.dp)
            ) {
                Text(
                    "서버 등록",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "QR에서 읽어온 정보를 확인해주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Text3,
                )
                Spacer(Modifier.height(18.dp))

                FilledField("서버 별칭", "Production Server") {}
                FilledField("IP 주소", "192.168.1.100", mono = true) {}
                FilledField("포트", "8443", mono = true) {}
                FilledField("서버 공개키", "aabbccdd...", mono = true, password = true) {}

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("등록하기", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("취소", color = Text3, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Server Register Form (EDIT with error)")
@Composable
private fun ServerRegisterFormEditPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface(color = Card) {
            Column(
                Modifier
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp, top = 16.dp)
            ) {
                Text(
                    "서버 수정",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "QR에서 읽어온 정보를 확인해주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Text3,
                )
                Spacer(Modifier.height(18.dp))

                Text(
                    "포트 범위가 올바르지 않습니다.",
                    color = Bad,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FilledField("서버 별칭", "Staging") {}
                FilledField("IP 주소", "10.0.0.5", mono = true) {}
                FilledField("포트", "99999", mono = true) {}
                FilledField("서버 공개키", "", mono = true, password = true) {}

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("수정하기", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("취소", color = Text3, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
