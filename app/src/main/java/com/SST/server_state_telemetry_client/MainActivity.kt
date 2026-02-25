package com.SST.server_state_telemetry_client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Server_State_Telemetry_ClientTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                            navController = navController,
                            startDestination = Screen.Dashboard.route
                    ) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(navController = navController)
                        }
                        composable(Screen.QrScan.route) {
                            QrScan(
                                    onQrScanned = { qrText ->
                                        navController.previousBackStackEntry?.savedStateHandle?.set(
                                                "qr_text",
                                                qrText
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
