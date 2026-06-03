package com.SST.server_state_telemetry_client.domain.model

data class RegistedServerList(
        val id: Int,
        val ip: String,
        val name: String,
        val status: Boolean,
        val port: Int = 443,
        val pubKey: String = ""
)
