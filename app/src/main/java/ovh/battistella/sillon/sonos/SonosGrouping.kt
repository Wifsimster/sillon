package ovh.battistella.sillon.sonos

/** What to send so the picked rooms play together, led by [leader]. */
data class GroupPlan(val leader: SonosZone, val leave: List<SonosZone>, val join: List<SonosZone>) {
    val isEmpty: Boolean get() = leave.isEmpty() && join.isEmpty()
}

/** Turns a set of picked rooms into the group Sonos should form, from the household's [ZoneGroups]. */
object SonosGrouping {
    /**
     * The room that leads: the [current] coordinator when it stays picked (the music doesn't move),
     * else a picked room already leading a group, else the first picked room.
     */
    fun leader(rooms: List<SonosZone>, topology: List<SonosGroup>, current: SonosZone?): SonosZone {
        require(rooms.isNotEmpty()) { "at least one room" }
        val picked = rooms.map { it.uuid }.toSet()
        current?.takeIf { it.uuid in picked }?.let { kept -> return rooms.first { it.uuid == kept.uuid } }
        val leading = topology.map { it.coordinator.uuid }.toSet()
        return rooms.firstOrNull { it.uuid in leading } ?: rooms.first()
    }

    /**
     * Rooms of the leader's group that weren't picked leave it; the leader leaves a group it doesn't
     * lead; picked rooms outside the leader's group join it.
     */
    fun plan(rooms: List<SonosZone>, topology: List<SonosGroup>, leader: SonosZone): GroupPlan {
        val picked = rooms.map { it.uuid }.toSet()
        val home = topology.firstOrNull { group -> group.members.any { it.uuid == leader.uuid } }
        val leads = home?.coordinator?.uuid == leader.uuid
        val leave = when {
            home == null -> emptyList()
            leads -> home.members.filter { it.uuid != leader.uuid && it.uuid !in picked }
            home.members.size > 1 -> listOf(leader)
            else -> emptyList()
        }
        val alreadyIn = if (leads) home.members.map { it.uuid }.toSet() else setOf(leader.uuid)
        val join = rooms.filter { it.uuid !in alreadyIn }.distinctBy { it.uuid }
        return GroupPlan(leader, leave, join)
    }

    /** The group as Sonos reports it once formed, or as Sillon asked for it when the report doesn't show it yet. */
    fun formed(rooms: List<SonosZone>, topology: List<SonosGroup>, leader: SonosZone): SonosGroup {
        val picked = rooms.map { it.uuid }.toSet()
        topology.firstOrNull { it.coordinator.uuid == leader.uuid && it.members.map(SonosZone::uuid).toSet() == picked }?.let { return it }
        val id = topology.firstOrNull { it.coordinator.uuid == leader.uuid }?.id ?: leader.uuid
        return SonosGroup(id, leader, listOf(leader) + rooms.filter { it.uuid != leader.uuid }.distinctBy { it.uuid })
    }

    /** Every visible room of the household, once each, sorted by name. */
    fun rooms(topology: List<SonosGroup>): List<SonosZone> =
        topology.flatMap { it.members }.distinctBy { it.uuid }.sortedBy { it.name.lowercase() }
}
