package com.smartsilentzone.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable ORDER BY id DESC")
    fun observeAll(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetable WHERE dayOfWeek = :day ORDER BY startTime ASC")
    fun observeByDay(day: String): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetable WHERE enabled = 1")
    suspend fun getEnabledSync(): List<TimetableEntity>

    @Upsert
    suspend fun upsert(item: TimetableEntity)

    @Query("DELETE FROM timetable WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM timetable")
    suspend fun deleteAll()
}
