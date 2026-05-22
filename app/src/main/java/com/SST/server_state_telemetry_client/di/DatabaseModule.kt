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

    /**
     * SQLite 3.25 미만 구형 OS 호환성을 완벽하게 보장하기 위해,
     * RENAME COLUMN 대신 새 임시 테이블을 복사하여 리네임하는 안전한 마이그레이션 수행
     */
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
            // name, ip, port 등 기본 서버 연결 설정은 그대로 유지
            // 기존 64자 hmacKey는 신규 32자 hashKey와 무관하므로 기본 빈 문자열로 치환 유도
            db.execSQL("INSERT INTO servers_new (id, name, ip, port, hashKey) SELECT id, name, ip, port, '' FROM servers")
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
        ).addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }
}

