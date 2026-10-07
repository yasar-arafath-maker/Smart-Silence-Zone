package com.smartsilentzone.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM important_contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM important_contacts WHERE enabled = 1")
    suspend fun getEnabledSync(): List<ContactEntity>

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Query("DELETE FROM important_contacts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM important_contacts")
    suspend fun deleteAll()
}
