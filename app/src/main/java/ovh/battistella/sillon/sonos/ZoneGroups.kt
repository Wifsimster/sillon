package ovh.battistella.sillon.sonos

/** One room (or one speaker) of the household. */
data class SonosZone(val uuid: String, val name: String, val baseUrl: String)

/** Rooms playing together; Sillon only ever talks to the [coordinator]. */
data class SonosGroup(val id: String, val coordinator: SonosZone, val members: List<SonosZone>) {
    /** "Living room", or "Living room + Kitchen" with the coordinator first. */
    val name: String get() = (listOf(coordinator) + members.filter { it.uuid != coordinator.uuid }).joinToString(" + ") { it.name }
}

object ZoneGroups {
    /**
     * The groups in a `ZoneGroupState` (already unescaped once by the SOAP layer). Hidden members
     * (a Sub, surround speakers, the second speaker of a stereo pair) are left out; a group whose
     * coordinator can't be placed is dropped.
     */
    fun parse(zoneGroupState: String): List<SonosGroup> {
        if (zoneGroupState.isBlank()) return emptyList()
        val root = runCatching { parseXml(zoneGroupState) }.getOrNull() ?: return emptyList()
        return root.descendants().filter { it.localName() == "ZoneGroup" }.mapNotNull { group ->
            val coordinatorId = group.attr("Coordinator") ?: return@mapNotNull null
            val members = group.childElements()
                .filter { it.localName() == "ZoneGroupMember" && it.attr("Invisible") != "1" }
                .mapNotNull { member ->
                    val uuid = member.attr("UUID") ?: return@mapNotNull null
                    val base = member.attr("Location")?.let(::baseUrlOf) ?: return@mapNotNull null
                    SonosZone(uuid, member.attr("ZoneName")?.takeIf { it.isNotBlank() } ?: uuid, base)
                }
            val coordinator = members.firstOrNull { it.uuid == coordinatorId } ?: return@mapNotNull null
            SonosGroup(group.attr("ID") ?: coordinatorId, coordinator, members)
        }.sortedBy { it.name.lowercase() }.toList()
    }
}
