package com.SST.server_state_telemetry_client.di

import android.content.Context
import androidx.room.Room
import com.SST.server_state_telemetry_client.data.local.AppDatabase
import com.SST.server_state_telemetry_client.data.local.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val dbName = "sst_database.db"
        return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, dbName)
            .fallbackToDestructiveMigration()
            .build()
    }


    @Provides
    @Singleton
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }
}
