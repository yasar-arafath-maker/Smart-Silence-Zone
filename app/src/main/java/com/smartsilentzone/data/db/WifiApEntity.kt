package com.smartsilentzone.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_aps")
data class WifiApEntity(
    @PrimaryKey
    val bssid: String, // e.g. "e4:5d:51:22:33:44" normalized uppercase or lowercase
    val zoneName: String, // e.g. "CSE 3rd Floor Lab"
    val floor: String, // e.g. "3rd Floor"
    val silentPolicy: String // "SILENT", "VIBRATE", "NORMAL"
)
