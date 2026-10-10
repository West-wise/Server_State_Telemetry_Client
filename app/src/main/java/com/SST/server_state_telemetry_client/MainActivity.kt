package com.SST.server_state_telemetry_client

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.SST.server_state_telemetry_client.data.repository.AppUpdateRepository
import com.SST.server_state_telemetry_client.domain.model.AppUpdate
import com.SST.server_state_telemetry_client.domain.model.validReleaseUrl
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.SST.server_state_telemetry_client.presentation.navigation.Screen
import com.SST.server_state_telemetry_client.presentation.ui.screens.DashboardScreen
import com.SST.server_state_telemetry_client.presentation.ui.screens.QrScan
import com.SST.server_state_telemetry_client.presentation.ui.screens.SplashScreen
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject internal lateinit var updates: AppUpdateRepository
    private var availableUpdate by mutableStateOf<AppUpdate?>(null)
    private var updateJob: Job? = null
    private var currentVersionName = ""

    override fun onStart() {
        super.onStart()
        if (updateJob?.isActive == true) return
        updateJob = lifecycleScope.launch {
            val info = packageManager.getPackageInfo(packageName, 0)
            currentVersionName = info.versionName.orEmpty()
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            availableUpdate = updates.check(code, Build.VERSION.SDK_INT)
        }
    }

    private fun postponeUpdate(update: AppUpdate) {
        updates.dismiss(update)
        availableUpdate = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Server_State_Telemetry_ClientTheme {
                availableUpdate?.let { update ->
                    AlertDialog(
                        onDismissRequest = { postponeUpdate(update) },
                        title = { Text("새 버전이 있습니다.") },
                        text = { Text("현재 버전: $currentVersionName\n새 버전: ${update.versionName}") },
                        confirmButton = {
                            TextButton(onClick = {
                                if (validReleaseUrl(update.releaseUrl)) {
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
                                            .addCategory(Intent.CATEGORY_BROWSABLE))
                                        postponeUpdate(update)
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        availableUpdate = null
                                    } catch (_: SecurityException) {
                                        availableUpdate = null
                                    }
                                }
                            }) { Text("업데이트") }
                        },
                        dismissButton = {
                            TextButton(onClick = { postponeUpdate(update) }) { Text("나중에") }
                        }
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route
                    ) {
                        composable(Screen.Splash.route) {
                            SplashScreen(navController = navController)
                        }
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(navController = navController)
                        }
                        composable(Screen.QrScan.route) {
                            QrScan(
                                onQrScanned = { qrText ->
                                    navController.previousBackStackEntry?.savedStateHandle?.set(
                                        "qr_text", qrText
                                    )
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.ServerDetail.route) { navBackStackEntry ->
                            val idStr = navBackStackEntry.arguments?.getString("id")
                            val id = idStr?.toIntOrNull() ?: -1
                            com.SST.server_state_telemetry_client.presentation.ui.screens
                                .ServerDetailScreen(
                                    serverId = id,
                                    onBack = { navController.popBackStack() }
                                )
                        }
                    }
                }
            }
        }
    }
}
