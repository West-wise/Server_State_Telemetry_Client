package com.SST.server_state_telemetry_client

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.pm.PackageInfoCompat
import com.SST.server_state_telemetry_client.presentation.ui.components.AppUpdateDialog
import com.SST.server_state_telemetry_client.presentation.viewmodel.AppUpdateViewModel
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
    private val appUpdates: AppUpdateViewModel by viewModels()
    private val currentVersionName by lazy { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() }
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) {
            appUpdates.install { intent -> startActivity(intent) }
        } else {
            Toast.makeText(this, "설치 권한을 허용해야 업데이트할 수 있습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        val info = packageManager.getPackageInfo(packageName, 0)
        appUpdates.check(PackageInfoCompat.getLongVersionCode(info), Build.VERSION.SDK_INT)
    }

    private fun installUpdate() {
        if (packageManager.canRequestPackageInstalls()) {
            appUpdates.install { intent -> startActivity(intent) }
        } else {
            try {
                installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "설정에서 이 앱의 설치 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Toast.makeText(this, "이 기기에서는 앱 설치가 제한되어 있습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Server_State_Telemetry_ClientTheme {
                appUpdates.state?.let { state ->
                    AppUpdateDialog(
                        state = state,
                        currentVersion = currentVersionName,
                        preparingInstall = appUpdates.preparingInstall,
                        onDownload = appUpdates::download,
                        onInstall = ::installUpdate,
                        onDismiss = appUpdates::dismiss
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
