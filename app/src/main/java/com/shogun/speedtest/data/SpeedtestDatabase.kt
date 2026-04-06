package com.shogun.speedtest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SpeedtestResult::class, WorkerLog::class],
    version = 9,
    exportSchema = false
)
abstract class SpeedtestDatabase : RoomDatabase() {

    abstract fun speedtestDao(): SpeedtestDao
    abstract fun workerLogDao(): WorkerLogDao

    companion object {
        @Volatile
        private var INSTANCE: SpeedtestDatabase? = null

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE speedtest_results ADD COLUMN is_ca TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE speedtest_results ADD COLUMN sync_failed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): SpeedtestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpeedtestDatabase::class.java,
                    "speedtest_db"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
