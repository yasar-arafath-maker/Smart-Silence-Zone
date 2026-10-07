package com.smartsilentzone.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ZoneEntity::class, TimetableEntity::class, ContactEntity::class, HistoryEntity::class, WifiApEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun timetableDao(): TimetableDao
    abstract fun contactDao(): ContactDao
    abstract fun historyDao(): HistoryDao
    abstract fun wifiApDao(): WifiApDao
}
