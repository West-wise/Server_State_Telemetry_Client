package com.SST.server_state_telemetry_client.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    // v1→v2: hmacKey → hashKey (Gen2 SipHash 마이그레이션)
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE servers_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    ip TEXT NOT NULL,
                    port INTEGER NOT NULL,
                    hashKey TEXT NOT NULL DEFAULT ''
                )
            """)
            db.execSQL("INSERT INTO servers_new (id, name, ip, port, hashKey) SELECT id, name, ip, port, '' FROM servers")
            db.execSQL("DROP TABLE servers")
            db.execSQL("ALTER TABLE servers_new RENAME TO servers")
        }
    }

    // v2→v3: hashKey → pubKey (Gen3 Noise XX 마이그레이션)
    // 기존 hashKey(SipHash 키)는 pubKey(X25519 공개키)와 호환 불가 → 빈 값으로 치환
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE servers_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    ip TEXT NOT NULL,
                    port INTEGER NOT NULL,
                    pubKey TEXT NOT NULL DEFAULT ''
                )
            """)
            db.execSQL("INSERT INTO servers_new (id, name, ip, port) SELECT id, name, ip, port FROM servers")
            db.execSQL("DROP TABLE servers")
            db.execSQL("ALTER TABLE servers_new RENAME TO servers")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sst_database"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }
}
