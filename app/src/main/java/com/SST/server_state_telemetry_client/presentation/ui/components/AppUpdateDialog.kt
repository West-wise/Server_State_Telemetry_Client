package com.SST.server_state_telemetry_client.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.SST.server_state_telemetry_client.presentation.viewmodel.AppUpdateState

@Composable
internal fun AppUpdateDialog(
    state: AppUpdateState,
    currentVersion: String,
    preparingInstall: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!preparingInstall) onDismiss() },
        title = { Text("앱 업데이트") },
        text = {
            Column {
                Text("현재 버전: $currentVersion\n새 버전: ${state.update.versionName}")
                when (state) {
                    is AppUpdateState.Available -> Text("업데이트를 다운로드한 뒤 설치할 수 있습니다.")
                    is AppUpdateState.Downloading -> {
                        LinearProgressIndicator()
                        Text("다운로드 및 검증 중: ${state.bytes / 1024} KB" +
                            (state.total?.let { " / ${it / 1024} KB" } ?: ""))
                    }
                    is AppUpdateState.Ready -> Text(if (preparingInstall) "설치 파일 확인 중…" else
                        "다운로드가 완료되었습니다. 설치 권한을 허용한 뒤 Android 화면에서 설치를 확인해 주세요.")
                    is AppUpdateState.Failed -> Text("업데이트를 준비하지 못했습니다. 연결 상태와 배포 파일을 확인한 뒤 다시 시도해 주세요.")
                }
            }
        },
        confirmButton = {
            when (state) {
                is AppUpdateState.Available, is AppUpdateState.Failed ->
                    TextButton(onClick = onDownload) { Text(if (state is AppUpdateState.Failed) "다시 시도" else "업데이트") }
                is AppUpdateState.Ready ->
                    TextButton(onClick = onInstall, enabled = !preparingInstall) { Text("설치") }
                is AppUpdateState.Downloading -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !preparingInstall) {
                Text(if (state is AppUpdateState.Downloading) "다운로드 취소" else "나중에")
            }
        }
    )
}
