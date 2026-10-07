package com.smartsilentzone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiApDao {
    @Query("SELECT * FROM wifi_aps")
    fun getAllAps(): Flow<List<WifiApEntity>>

    @Query("SELECT * FROM wifi_aps WHERE bssid = :bssid LIMIT 1")
    suspend fun getByBssid(bssid: String): WifiApEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(ap: WifiApEntity)

    @Query("DELETE FROM wifi_aps WHERE bssid = :bssid")
    suspend fun deleteByBssid(bssid: String)
}
