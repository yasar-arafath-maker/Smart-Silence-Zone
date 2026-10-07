package com.smartsilentzone.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDateTime: String,
    val zoneName: String,
    val eventType: String, // "Sustained Entry (30s DWELL)", "Debounced Exit Verified", "Emergency Override"
    val previousMode: String,
    val newMode: String
)
