package com.smartsilentzone.domain

enum class RingerAction { NORMAL, VIBRATE, SILENT, DND }

/** Higher priority wins when a user is inside overlapping zones. */
enum class ZoneType(val priority: Int, val defaultAction: RingerAction?) {
    COLLEGE(0, null),            // campus boundary: no action by itself
    CANTEEN(2, RingerAction.NORMAL),
    BREAK_ZONE(2, RingerAction.NORMAL),
    LIBRARY(3, RingerAction.SILENT),
    CLASSROOM(4, RingerAction.SILENT),
    EXAM_HALL(5, RingerAction.DND)
}

data class Zone(
    val id: String,
    val name: String,
    val type: ZoneType,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val actionOverride: RingerAction? = null,
    val enabled: Boolean = true
) {
    val action: RingerAction? get() = actionOverride ?: type.defaultAction
}

interface ZoneRepository {
    val zones: kotlinx.coroutines.flow.Flow<List<Zone>>
    suspend fun getZones(): List<Zone>
}

sealed interface Decision {
    data object NoChange : Decision
    data class Apply(val action: RingerAction, val cause: Zone?, val reason: String) : Decision
}
