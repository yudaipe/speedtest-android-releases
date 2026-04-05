package com.shogun.speedtest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SpeedtestResult::class, WorkerLog::class],
    version = 6,
    exportSchema = false
)
abstract class SpeedtestDatabase : RoomDatabase() {

    abstract fun speedtestDao(): SpeedtestDao
    abstract fun workerLogDao(): WorkerLogDao

    companion object {
        @Volatile
        private var INSTANCE: SpeedtestDatabase? = null

        fun getInstance(context: Context): SpeedtestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpeedtestDatabase::class.java,
                    "speedtest_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
