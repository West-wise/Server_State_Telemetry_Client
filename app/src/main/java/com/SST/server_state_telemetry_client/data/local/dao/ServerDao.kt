package com.SST.server_state_telemetry_client.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.SST.server_state_telemetry_client.data.local.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers") fun getAllServers(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update suspend fun updateServer(server: ServerEntity)

    @Delete suspend fun deleteServer(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id") suspend fun deleteServerById(id: Int)
}
