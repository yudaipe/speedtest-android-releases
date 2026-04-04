package com.shogun.speedtest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SpeedtestResult::class],
    version = 10,
    exportSchema = false
)
abstract class SpeedtestDatabase : RoomDatabase() {

    abstract fun speedtestDao(): SpeedtestDao

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
