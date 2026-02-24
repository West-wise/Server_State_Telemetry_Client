package com.SST.server_state_telemetry_client.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Dashboard : Screen("dashboard_screen")
    object QrScan : Screen("qr_scan")
    object ServerDetail : Screen("server_detail/{id}"){
        fun routeWithId(id: Int) = "server_detail/$id"
    }
}
