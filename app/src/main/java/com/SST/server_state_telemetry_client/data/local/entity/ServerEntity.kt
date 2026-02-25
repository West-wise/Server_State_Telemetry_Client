package com.SST.server_state_telemetry_client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val name: String,
        val ip: String,
        val port: Int,
        val hmacKey: String
)
