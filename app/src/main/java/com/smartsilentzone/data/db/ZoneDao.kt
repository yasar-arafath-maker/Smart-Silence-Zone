package com.smartsilentzone.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones")
    suspend fun getAll(): List<ZoneEntity>

    @Upsert
    suspend fun upsert(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM zones")
    suspend fun deleteAll()
}
