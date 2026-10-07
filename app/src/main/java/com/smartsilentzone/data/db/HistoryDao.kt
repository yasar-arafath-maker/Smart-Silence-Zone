package com.smartsilentzone.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
