package com.SST.server_state_telemetry_client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.SST.server_state_telemetry_client.data.local.dao.ServerDao
import com.SST.server_state_telemetry_client.data.local.entity.ServerEntity

@Database(entities = [ServerEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
}
