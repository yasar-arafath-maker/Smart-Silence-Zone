package com.smartsilentzone.domain

import javax.inject.Inject

/** Pure logic, with no Android dependencies, so it is trivially unit-testable. */
class ZoneEvaluator @Inject constructor() {

    fun decide(zones: List<Zone>, confirmedInsideIds: Set<String>): Decision {
        val active = zones.filter { it.enabled }
        if (active.none { it.type == ZoneType.COLLEGE }) return Decision.NoChange

        val inside = active.filter { it.id in confirmedInsideIds }

        // Outside everything -> strict unsilent
        if (inside.isEmpty()) {
            return Decision.Apply(RingerAction.NORMAL, null, "Outside college")
        }

        // Highest priority wins; ties go to the smaller (more precise) zone.
        val winner = inside.maxWithOrNull(
            compareBy<Zone> { it.type.priority }.thenByDescending { -it.radiusMeters }
        )!!

        val action = winner.action
            ?: return Decision.NoChange // inside campus but in no sub-zone: leave ringer alone

        return Decision.Apply(action, winner, "Inside ${winner.name}")
    }
}
