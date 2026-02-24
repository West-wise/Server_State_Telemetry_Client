package com.SST.server_state_telemetry_client.domain.model

data class RegistedServerList(
    val id: Int,        // ID(구분값)
    val ip: String,     // 서버의 IP 주소값
    val name: String,   // 등록시 설정한 별칭
    val status: Boolean // 등록은 되어있으나 서버가 활성화 되어있는지.
)