package com.shogun.speedtest.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WorkerLogDao {

    @Insert
    suspend fun insert(log: WorkerLog): Long

    @Query("SELECT * FROM worker_log WHERE isSynced = 0 ORDER BY id ASC")
    suspend fun getUnsynced(): List<WorkerLog>

    @Query("UPDATE worker_log SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
}
