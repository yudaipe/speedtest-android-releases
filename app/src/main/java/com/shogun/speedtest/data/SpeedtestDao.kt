package com.shogun.speedtest.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedtestDao {

    @Insert
    suspend fun insert(result: SpeedtestResult): Long

    @Query("SELECT * FROM speedtest_results WHERE isSynced = 0 AND sync_failed = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<SpeedtestResult>

    @Query("UPDATE speedtest_results SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("UPDATE speedtest_results SET sync_failed = 1 WHERE id = :id")
    suspend fun markAsSyncFailed(id: Long)

    @Query("SELECT * FROM speedtest_results ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<SpeedtestResult>

    @Query("SELECT * FROM speedtest_results ORDER BY timestamp DESC LIMIT 5")
    fun getRecentFlow(): Flow<List<SpeedtestResult>>
}
