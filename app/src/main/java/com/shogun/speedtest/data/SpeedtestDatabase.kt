package com.shogun.speedtest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SpeedtestResult::class, WorkerLog::class],
    version = 8,
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

        fun getInstance(context: Context): SpeedtestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpeedtestDatabase::class.java,
                    "speedtest_db"
                )
                    .addMigrations(MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
