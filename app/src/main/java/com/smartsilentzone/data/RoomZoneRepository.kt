package com.smartsilentzone.data

import com.smartsilentzone.data.db.ZoneDao
import com.smartsilentzone.data.db.toDomain
import com.smartsilentzone.data.db.toEntity
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.domain.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Read + write contract. The evaluator, worker and VM only need the read-only [ZoneRepository]. */
interface MutableZoneRepository : ZoneRepository {
    suspend fun upsert(zone: Zone)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

@Singleton
class RoomZoneRepository @Inject constructor(private val dao: ZoneDao) : MutableZoneRepository {

    override val zones: Flow<List<Zone>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getZones(): List<Zone> = dao.getAll().map { it.toDomain() }

    override suspend fun upsert(zone: Zone) {
        require(zone.name.isNotBlank()) { "Zone name required" }
        require(zone.latitude in -90.0..90.0 && zone.longitude in -180.0..180.0) { "Invalid coordinates" }
        require(zone.radiusMeters >= 20f) { "Radius too small for reliable geofencing" }
        dao.upsert(zone.toEntity())
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
