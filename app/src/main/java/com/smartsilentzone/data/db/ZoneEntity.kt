package com.smartsilentzone.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartsilentzone.domain.RingerAction
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.domain.ZoneType

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val actionOverride: String?,
    val enabled: Boolean
)

fun ZoneEntity.toDomain() = Zone(
    id = id,
    name = name,
    // Unknown value (e.g. written by a newer app version) degrades to the no-action campus type.
    type = runCatching { ZoneType.valueOf(type) }.getOrDefault(ZoneType.COLLEGE),
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    actionOverride = actionOverride?.let { runCatching { RingerAction.valueOf(it) }.getOrNull() },
    enabled = enabled
)

fun Zone.toEntity() = ZoneEntity(
    id = id,
    name = name,
    type = type.name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    actionOverride = actionOverride?.name,
    enabled = enabled
)
