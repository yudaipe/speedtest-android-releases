package com.shogun.speedtest.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "worker_log")
data class WorkerLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    val result: String? = null,
    @ColumnInfo(name = "error_reason")
    val errorReason: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    val isSynced: Boolean = false
)
