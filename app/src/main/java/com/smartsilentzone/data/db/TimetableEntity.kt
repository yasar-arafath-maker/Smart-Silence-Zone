package com.smartsilentzone.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartsilentzone.domain.RingerAction

@Entity(tableName = "timetable")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectName: String,
    val dayOfWeek: String, // "Monday", "Tuesday", etc.
    val startTime: String, // e.g. "09:00 AM"
    val endTime: String,   // e.g. "10:00 AM"
    val zoneId: String,
    val zoneName: String,
    val soundMode: String, // "SILENT", "VIBRATE", "DND", "NORMAL"
    val enabled: Boolean = true
) {
    val ringerAction: RingerAction
        get() = runCatching { RingerAction.valueOf(soundMode) }.getOrDefault(RingerAction.SILENT)
}
