package com.smartsilentzone.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "important_contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String = "Family", // "Family", "Professor", "Emergency"
    val enabled: Boolean = true
)
